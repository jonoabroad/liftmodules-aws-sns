package net.liftmodules.aws.sns

import java.net.InetAddress

import net.liftweb.actor.LiftActor
import net.liftweb.common.Box.box2Option
import net.liftweb.common.Loggable
import net.liftweb.http.LiftRulesMocker.toLiftRules
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.LiftRules
import net.liftweb.http.OkResponse
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.parse
import net.liftweb.util.Helpers.tryo

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sns.model.ConfirmSubscriptionRequest
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sns.model.SubscribeRequest
import com.amazonaws.services.sns.model.UnsubscribeRequest
import com.amazonaws.services.sns.AmazonSNSClient

import SNS.HandlerFunction

sealed trait SNSMsg
case class Publish(msg:String) extends SNSMsg

object Protocol extends Enumeration("http","https") {
    type Protocol = Value
    val HTTP, HTTPS = Value    
}

object SNS {
type Payload= JValue
type HandlerFunction = PartialFunction[Payload,Unit]
}
case class AWSCreds(access:String,secret:String)
case class SNSConfig(creds:AWSCreds,arn:String,path:List[String], protocol:Protocol.Value = Protocol.HTTPS,address:String = InetAddress.getLocalHost().getHostAddress())
case class SNS(config:SNSConfig,handler: HandlerFunction) extends RestHelper with LiftActor with Loggable {
  
  val endpoint = "%s://%s/%s".format(config.protocol.toString,config.address,config.path.mkString("/"))
  val service = new AmazonSNSClient(new BasicAWSCredentials(config.creds.access,config.creds.secret));
  
  def init = {
      logger.info("Initialise SNS Service to %s".format(config.arn))
      LiftRules.statelessDispatch.append(this)    
      subscribe
      LiftRules.unloadHooks.append(() ⇒ this ! unsubscribe)    
  }
  

  object MsgType extends Enumeration("Notification", "SubscriptionConfirmation") {
    type MsgType = Value

    val Notification, SubscriptionConfirmation = Value

    def apply(ov: Option[String]): Option[MsgType.Value] = ov.flatMap { v ⇒ tryo { MsgType.withName(v) } }

  }
 
  serve({ 
    case config.path Post post ⇒
      implicit val formats = net.liftweb.json.DefaultFormats
      post.body.map { b ⇒
        val s = new String(b)
        logger.trace("Msg %s".format(s))      
        val json = parse(s)
        val tupe: Option[MsgType.Value] = MsgType((json \ "Type").extractOpt[String])
        tupe match {
          case Some(MsgType.SubscriptionConfirmation) ⇒
            for {
              token ← (json \ "Token").extractOpt[String]
              arn ← (json \ "TopicArn").extractOpt[String]
            } this ! confirmation(token, arn)
          case Some(MsgType.Notification) ⇒ handler.apply(json \ "Message")
          case otherwise ⇒ logger.error("Unknown message %s raw body %s".format(otherwise, s))
        }
      }
      OkResponse()
      
  })
  
  def messageHandler = {
    case p: Publish ⇒ service.publish(new PublishRequest().withTopicArn(config.arn).withMessage(p.msg))
  }

  private[this] var uarn: Option[String] = None  
  
  private[this] def subscribe = {
      logger.trace("subscribe to %s - protocol %s  endpoint %s".format(config.arn,config.protocol,endpoint))        
      service.subscribe(new SubscribeRequest().withTopicArn(config.arn).withProtocol(config.protocol.toString).withEndpoint(endpoint))  
  }
  
  private[this] def confirmation(token: String, arn: String) = { 
    uarn = Option(service.confirmSubscription(new ConfirmSubscriptionRequest().withTopicArn(arn).withToken(token)).getSubscriptionArn) 
    logger.trace("confirmation  %s".format(uarn))  
  }
  
  private[this] def unsubscribe = {
      uarn.map { u ⇒ service.unsubscribe(new UnsubscribeRequest().withSubscriptionArn(u)) }
      uarn = None
  }  

}