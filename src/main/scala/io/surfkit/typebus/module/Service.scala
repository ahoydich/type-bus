package io.surfkit.typebus.module

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.sksamuel.avro4s.{AvroInputStream, AvroSchema}
import io.surfkit.typebus.event._
import io.surfkit.typebus.{ByteStreamReader, ByteStreamWriter}
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait Service[UserBaseType] extends Module[UserBaseType]{

  def registerStream[T <: UserBaseType : ClassTag, U <: UserBaseType : ClassTag](f: (T) => Future[U]) (implicit reader: ByteStreamReader[T], writer: ByteStreamWriter[U]) =
    op(funToPF(f))

  def registerStream[T <: UserBaseType : ClassTag, U <: UserBaseType : ClassTag](f:  (T, EventMeta) => Future[U])  (implicit reader: ByteStreamReader[T], writer: ByteStreamWriter[U]) =
    op2(funToPF2(f))

  def registerStream[T <: UserBaseType : ClassTag](f:  (T, EventMeta) => Future[Unit])  (implicit reader: ByteStreamReader[T]) =
    op2Unit(funToPF2Unit(f))

  def startService(consumerSettings: ConsumerSettings[Array[Byte], Array[Byte]], replyTo: ActorRef)(implicit system: ActorSystem) = {
    import system.dispatcher
    val decider: Supervision.Decider = {
      case _ => Supervision.Resume  // Never give up !
    }

    implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    val replyAndCommit = new PartialFunction[(ConsumerMessage.CommittableMessage[Array[Byte], Array[Byte]],PublishedEvent, Any), Future[Done]]{
      def apply(x: (ConsumerMessage.CommittableMessage[Array[Byte], Array[Byte]],PublishedEvent, Any) ) = {
        system.log.debug("TypeBus: replyAndCommit")
        system.log.debug(s"listOfImplicitsWriters: ${listOfImplicitsWriters}")
        system.log.debug(s"type: ${x._3.getClass.getCanonicalName}")
        if(x._3 != Unit) {
          implicit val timeout = Timeout(4 seconds)
          val writer = listOfImplicitsWriters(x._3.getClass.getCanonicalName)
          system.log.debug(s"TypeBus writer: ${writer}")
          replyTo ! PublishedEvent(
            meta = x._2.meta.copy(
              eventId = UUID.randomUUID.toString,
              eventType = x._3.getClass.getCanonicalName,
              responseTo = Some(x._2.meta.eventId)
            ),
            payload = writer.write(x._3.asInstanceOf[UserBaseType]))
        }
        x._1.committableOffset.commitScaladsl()
      }
      def isDefinedAt(x: (ConsumerMessage.CommittableMessage[Array[Byte], Array[Byte]],PublishedEvent, Any) ) = true
    }

    system.log.debug(s"STARTING TO LISTEN ON TOPICS:\n ${listOfTopics}")

    Consumer.committableSource(consumerSettings, Subscriptions.topics(listOfTopics:_*))
      .mapAsyncUnordered(4) { msg =>
        system.log.debug(s"TypeBus: got msg for topic: ${msg.record.topic()}")
        try {
          val schema = AvroSchema[PublishedEvent]
          val reader = listOfImplicitsReaders(msg.record.topic())
          val input = AvroInputStream.binary[PublishedEvent].from(msg.record.value()).build(schema)
          val result = input.iterator.toSeq
          system.log.debug(s"TypeBus: got result: ${result}")
          val publish = result.head
          system.log.debug(s"TypeBus: got publish: ${publish}")
          system.log.debug(s"TypeBus: reader: ${reader}")
          system.log.debug(s"publish.payload.size: ${publish.payload.size}")
          val payload = reader.read(publish.payload)
          system.log.debug(s"TypeBus: got payload: ${payload}")
          if(handleEventWithMetaUnit.isDefinedAt( (payload, publish.meta) ) )
            handleEventWithMetaUnit( (payload, publish.meta) ).map(x => (msg, publish, x))
          else if(handleEventWithMeta.isDefinedAt( (payload, publish.meta) ) )
            handleEventWithMeta( (payload, publish.meta)  ).map(x => (msg, publish, x))
          else
            handleEvent(payload).map(x => (msg, publish, x))
        }catch{
          case t:Throwable =>
            t.printStackTrace()
            throw t
        }
      }
      .mapAsyncUnordered(4)(replyAndCommit)
      .runWith(Sink.ignore)
  }
}
