package models

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.functional.syntax._


/* Monero network status objects */

case class BlockchainStatus(height: Long)

object BlockchainStatus {
  implicit val blockchainStatusReads: Reads[BlockchainStatus] = 
      (JsPath \ "height").read[Long].map(BlockchainStatus(_))

  implicit val blockchainStatusWrites: Writes[BlockchainStatus] = 
    (__ \ 'height).write[Long].contramap(_.height)
}

case class MempoolStatus(transactions: List[Transaction])

object MempoolStatus {
  implicit val mempoolReads: Reads[MempoolStatus] = 
      (JsPath \ "transactions").read[List[Transaction]].map(MempoolStatus(_))

  implicit val mempoolWrites: Writes[MempoolStatus] = 
    (__ \ 'transactions).write[List[Transaction]].contramap(_.transactions)
}

case class Transaction(id: String, size: Long, fee: Long)

object Transaction {
  implicit val transactionReads: Reads[Transaction] = (
      (JsPath \ "id_hash").read[String] and
      (JsPath \ "blob_size").read[Long] and
      (JsPath \ "fee").read[Long]
    )(Transaction.apply _)

  implicit val transactionWrites: Writes[Transaction] = (
      (JsPath \ "id_hash").write[String] and
      (JsPath \ "blob_size").write[Long] and
      (JsPath \ "fee").write[Long]
    )(unlift(Transaction.unapply))
}

/* MoneroNode communicates with monero nodes on the network. */

case class MoneroNode(url: String, ws: WSClient) {
  val heightUrl = s"http://${url}/getheight"
  val poolUrl = s"http://${url}/get_transaction_pool"

  def getApiData(url: String)(
    implicit ctx: ExecutionContext): Future[JsValue] = {

    val response: Future[WSResponse] =
      ws.url(url).withRequestTimeout(5000 millis).get()
    response map { r => r.json }
  }

  def height(implicit ctx: ExecutionContext): Future[Long] = {
    val apiData = getApiData(heightUrl)
    apiData map { d => (d \ "height").as[Long] }
  }

  def mempool(implicit ctx: ExecutionContext): Future[List[Transaction]] = {
    val apiData = getApiData(poolUrl)
    apiData map { data => transactionsFromJson(data) }
  }

  def transactionsFromJson(data: JsValue): List[Transaction] = {
    val transactions = (data \ "transactions").validate[List[Transaction]]
    transactions.getOrElse(List[Transaction]())
  }

  override def toString = s"mn[${url}]"
}
