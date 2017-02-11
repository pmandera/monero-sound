package actors

import scala.concurrent.duration._
import scala.util.{ Success, Failure, Random }
import akka.actor._
import play.api.libs.ws._

import models._


case class StatusRequest()
case class ApiActorTick()
case class ApiActorStatus()
case class ApiActorNodeChange(reason: String)

/*
 * Actor making RPC requests using Monero nodes.
 *
 * It periodically connects to an active node and feches information about
 * the status of the blockchain and mempool and sends that information to the
 * StatusActor and PoolActor respectively. It can be configured to periodically
 * change the active node: after a certain number of requests or after a failed
 * request.
 *
 **/

object ApiActor {
  def props(statusActor: ActorRef, poolActor: ActorRef, ws: WSClient,
    ticksChange: Int, moneroNodes: List[MoneroNode], nodeFetchPeriod: Int,
    statusReportPeriod: Int) =
    Props(new ApiActor(statusActor, poolActor, ws: WSClient,
      ticksChange: Int, moneroNodes: List[MoneroNode], nodeFetchPeriod: Int,
      statusReportPeriod: Int))
}

class ApiActor(
  statusActor: ActorRef, poolActor: ActorRef, ws: WSClient,
  ticksChange: Int, moneroNodes: List[MoneroNode], nodeFetchPeriod: Int,
  statusReportPeriod: Int)
extends Actor with ActorLogging {
  import context.dispatcher

  var nTicks = 0
  var changingNode = false
  var currentNode: MoneroNode = Random.shuffle(moneroNodes).head

  log.info(s"ApiActor[${self.toString}] started, node: ${currentNode}")

  val tickData =
    context.system.scheduler.schedule(
      10 millis, nodeFetchPeriod millis, self, ApiActorTick())

  val tickStatus =
    context.system.scheduler.schedule(
      10 millis, statusReportPeriod seconds, self, ApiActorStatus())

  def changeNode(reason: String) = {
    log.info(
      s"ApiActor[${self.toString}] node ${currentNode} dropped after $nTicks ticks because of ${reason}")
    currentNode = Random.shuffle(moneroNodes).head
    log.info(s"ApiActor[${self.toString}] new node: ${currentNode}")
    nTicks = 0
  }

  def receive = {
    case ApiActorStatus() => log.info(
      s"ApiActor[${self}] node ${currentNode} ok after $nTicks ticks")
    case ApiActorNodeChange(reason) => changeNode(reason)
    case ApiActorTick() => {
      nTicks += 1

      val result = for {
        height <- currentNode.height
        mempool <- currentNode.mempool
      } yield (height, mempool)

      result onComplete {
        case Failure(t) => changeNode("request_error")
        case Success((height, mempool)) => {
          statusActor ! BlockchainStatus(height)
          poolActor ! MempoolStatus(mempool)

          if (nTicks >= ticksChange) { changeNode("scheduled_change") }
        }
      }
    }
  }
}

/** Actor monitoring status of the mempool.
  *
  * Receives messages from the ApiActors and broadcasts information about
  * new transactions.
  **/

object PoolActor {
  def props = Props(new PoolActor())
}

class PoolActor extends Actor with ActorLogging {
  var waitingTransactions: List[Transaction] = List()

  def receive = {
    case MempoolStatus(transactions) => {
      val newTransactions: List[Transaction] =
        transactions.diff(waitingTransactions)

      if (!newTransactions.isEmpty) {
        context.system.eventStream.publish(MempoolStatus(newTransactions))

        log.debug(s"Pool update, new transactions: ${newTransactions}")
        waitingTransactions = (newTransactions ++ waitingTransactions).take(200)
      }
    }
  }
}

/** Actor monitoring status of blockchain.
  *
  * Receives messages from the ApiActors and broadcasts information about
  * new blocks found.
  **/

object StatusActor {
  def props = Props(new StatusActor())
}

class StatusActor extends Actor with ActorLogging {
  var currentStatus: BlockchainStatus = BlockchainStatus(0)

  def receive = {
    case BlockchainStatus(height) => {
      if (currentStatus.height < height) {
        log.debug(s"Chain update, height: ${height.toString}")
        currentStatus = BlockchainStatus(height)
        context.system.eventStream.publish(currentStatus)
      } else if (currentStatus.height > (height + 1)) {
        sender ! ApiActorNodeChange(
          s"behind (${height - currentStatus.height})")
      }
    }
  }
}


/** Actor taking care of communication with a websocket client.
  *
  * Subscribes to all status changes messages that are broadcasted
  * by the StatusActor and PoolActor.
  *
  * Each new client connection has its own WebSocketActor.
  **/

object WebSocketActor {
  def props(out: ActorRef) = Props(new WebSocketActor(out))
}

class WebSocketActor(out: ActorRef)
  extends Actor with ActorLogging {
  context.system.eventStream.subscribe(self, classOf[BlockchainStatus])
  context.system.eventStream.subscribe(self, classOf[MempoolStatus])

  import play.api.libs.json._

  case class WebSocketEvent(action: String, data: JsValue)
  implicit val webSocketEventFormat = Json.format[WebSocketEvent]

  def receive = {
    case event: BlockchainStatus => {
      val wsEvent = WebSocketEvent("chain_update", Json.toJson(event))
      out ! Json.toJson(wsEvent).toString
    }

    case event: MempoolStatus => {
      for { t <- event.transactions } {
        val wsEvent = WebSocketEvent("new_transaction", Json.toJson(t))
        out ! Json.toJson(wsEvent).toString
      }
    }
  }

  override def preStart = { 
    log.debug(s"Started websocket actor: ${out.toString}")
  }

  override def postStop = { 
    log.debug(s"Stopped websocket actor: ${out.toString}")
  }
}
