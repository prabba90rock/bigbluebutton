package org.bigbluebutton.api2

import akka.actor.ActorSystem
import org.bigbluebutton.api2.bus._
import org.bigbluebutton.api2.endpoint.redis.{AppsRedisSubscriberActor, MessageSender, RedisPublisher}
import org.bigbluebutton.api2.meeting.MeetingsManagerActor

import scala.concurrent.duration._

class BbbWebApiGWApp(val oldMessageReceivedGW: OldMessageReceivedGW) extends SystemConfiguration{

  implicit val system = ActorSystem("bbb-apps-common")
  implicit val timeout = akka.util.Timeout(3 seconds)

  private val jsonMsgToAkkaAppsBus = new JsonMsgToAkkaAppsBus
  private val redisPublisher = new RedisPublisher(system)
  private val msgSender: MessageSender = new MessageSender(redisPublisher)
  private val messageSenderActorRef = system.actorOf(
    MessageSenderActor.props(msgSender), "messageSenderActor")

  jsonMsgToAkkaAppsBus.subscribe(messageSenderActorRef, toAkkaAppsJsonChannel)

  private val receivedJsonMsgBus = new JsonMsgFromAkkaAppsBus
  private val oldMessageEventBus = new OldMessageEventBus
  private val msgFromAkkaAppsEventBus = new MsgFromAkkaAppsEventBus
  private val msgToAkkaAppsEventBus = new MsgToAkkaAppsEventBus

  private val meetingManagerActorRef = system.actorOf(
    MeetingsManagerActor.props(msgToAkkaAppsEventBus), "meetingManagerActor")
  msgFromAkkaAppsEventBus.subscribe(meetingManagerActorRef, fromAkkaAppsChannel)

  private val appsRedisSubscriberActor = system.actorOf(
    AppsRedisSubscriberActor.props(receivedJsonMsgBus,oldMessageEventBus), "appsRedisSubscriberActor")

  private val receivedJsonMsgHdlrActor = system.actorOf(
    ReceivedJsonMsgHdlrActor.props(msgFromAkkaAppsEventBus), "receivedJsonMsgHdlrActor")

  receivedJsonMsgBus.subscribe(receivedJsonMsgHdlrActor, fromAkkaAppsJsonChannel)

  private val oldMessageJsonReceiverActor = system.actorOf(
    OldMessageJsonReceiverActor.props(oldMessageReceivedGW), "oldMessageJsonReceiverActor")

  oldMessageEventBus.subscribe(oldMessageJsonReceiverActor, fromAkkaAppsOldJsonChannel)

  /*****
    * External APIs for Gateway
    */
  def send(channel: String, json: String): Unit = {
    jsonMsgToAkkaAppsBus.publish(JsonMsgToAkkaAppsBusMsg(toAkkaAppsJsonChannel, new JsonMsgToSendToAkkaApps(channel, json)))
  }
}