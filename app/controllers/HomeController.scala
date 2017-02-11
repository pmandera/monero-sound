package controllers

import scala.concurrent.ExecutionContext
import scala.collection.JavaConversions._

import akka.actor._
import akka.event.Logging
import akka.stream.Materializer

import javax.inject._

import play.api.mvc._
import play.api.libs.streams._
import play.api.libs.ws.WSClient

import actors._
import models._

/* Main application controller. */

@Singleton
class HomeController @Inject()(implicit actorSystem: ActorSystem,
                               mat: Materializer,
                               executionContext: ExecutionContext,
                               ws: WSClient,
                               conf: play.api.Configuration
                             )
  extends Controller {

  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  private implicit val logging = Logging(
    actorSystem.eventStream, logger.getName)

  /** Load configuration */

  val xmrDonationAddress = conf.getString("xmr-donation-address").get
  val btcDonationAddress = conf.getString("btc-donation-address").get
  val nodeFetchPeriod = conf.getInt("node-fetch-period").get
  val timeJitter = conf.getInt("time-jitter").get
  val statusReportPeriod = conf.getInt("status-report-period").get
  val ticksChange = conf.getInt("ticks-change").get
  val noConnections = conf.getInt("no-connections").get
  val moneroNodeUrls = conf.getStringList("xmr-nodes").get.toList

  /** Create actors */

  val moneroNodes = moneroNodeUrls map { MoneroNode(_, ws) }

  val statusActor: ActorRef = actorSystem.actorOf(StatusActor.props)
  val poolActor: ActorRef = actorSystem.actorOf(PoolActor.props)

  val apiActors: List[ActorRef] = (1 to noConnections).toList map { _ =>
      actorSystem.actorOf(
        ApiActor.props(statusActor, poolActor, ws,
        ticksChange, moneroNodes,
        nodeFetchPeriod, statusReportPeriod))
    }

  /* Routed actions */

  def events = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef(out => WebSocketActor.props(out))
  }

  def index: Action[AnyContent] = Action { implicit request =>
    val url = routes.HomeController.events().webSocketURL()
    Ok(views.html.index(url, xmrDonationAddress, btcDonationAddress,
       timeJitter))
  }
}
