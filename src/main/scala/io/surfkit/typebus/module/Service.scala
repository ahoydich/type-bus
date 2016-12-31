package io.surfkit.typebus.module

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import io.surfkit.typebus.Mapper

import scala.concurrent.Future
import scala.concurrent.duration._
import io.surfkit.typebus.event._
import org.joda.time.DateTime

import scala.reflect.ClassTag

/**
  * Created by suroot on 21/12/16.
  */
trait Service[Api] extends Module{

  def perform[T <: m.Model : ClassTag](p: PartialFunction[T, Future[m.Model]]) = op(p)


  def startService(consumerSettings: ConsumerSettings[Array[Byte], String], mapper: Mapper, api: Api)(implicit system: ActorSystem) = {
    import system.dispatcher
    val decider: Supervision.Decider = {
      case _ => Supervision.Resume  // Never give up !
    }

    implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    val replyAndCommit = new PartialFunction[(ConsumerMessage.CommittableMessage[Array[Byte], String],PublishedEvent[_], Any), Future[Done]]{
      def apply(x: (ConsumerMessage.CommittableMessage[Array[Byte], String],PublishedEvent[_], Any) ) = {
        println("replyAndCommit")
        implicit val timeout = Timeout(4 seconds)
        //println(s"Doing actor selection and send: ${x._3} => ${x._2.source}")
        println(s"Doing actor selection and send: ${x._2.source}")
        system.actorSelection(x._2.source).resolveOne().flatMap { actor =>
          actor ! ResponseEvent(
            eventId = UUID.randomUUID.toString,
            eventType = x._3.getClass.getCanonicalName.replaceAll("\\$", ""),
            userIdentifier = x._2.userIdentifier,
            source = x._2.source,
            publishedAt = new DateTime(),
            occurredAt = new DateTime(),
            correlationId = x._2.correlationId,
            payload = x._3)
          x._1.committableOffset.commitScaladsl()
        }
      }
      def isDefinedAt(x: (ConsumerMessage.CommittableMessage[Array[Byte], String],PublishedEvent[_], Any) ) = true
    }

    Consumer.committableSource(consumerSettings, Subscriptions.topics(listOfTopics:_*))
      .mapAsyncUnordered(4) { msg =>
        val publish = mapper.readValue[PublishedEvent[m.Model]](msg.record.value())
        val event = publish.copy(payload = mapper.readValue[m.Model](mapper.writeValueAsString(publish.payload)) )    // FIXME: we have to write and read again .. grrr !!
        //println(s"event: ${event}")
        //println(s"event.payload: ${event.payload}")
        handleEvent(event.payload).map( x => (msg, event, x) )
      }
      .mapAsyncUnordered(4)(replyAndCommit)
      .runWith(Sink.ignore)
  }
}
