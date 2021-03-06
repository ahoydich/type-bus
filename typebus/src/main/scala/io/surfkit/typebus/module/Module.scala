package io.surfkit.typebus.module

import io.surfkit.typebus.{ByteStreamReader, ByteStreamWriter}
import io.surfkit.typebus.event._

import scala.concurrent.Future
import scala.reflect.ClassTag

/***
  * Module is the base of ALL typebus service.
  * @tparam UserBaseType - the base trait all your service types inherit from
  */
trait Module[UserBaseType] {

  var listOfPartials = List.empty[PartialFunction[_, Future[UserBaseType]]]
  var listOfPartialsWithMeta = List.empty[PartialFunction[_, Future[UserBaseType]]]
  var listOfPartialsWithMetaUnit = List.empty[PartialFunction[_, Future[Unit]]]
  var listOfImplicitsReaders = Map.empty[String, ByteStreamReader[UserBaseType] ]
  var listOfImplicitsWriters = Map.empty[String, ByteStreamWriter[UserBaseType] ]
  var listOfFunctions = List.empty[(String, String)]

  var listOfServicePartialsWithMeta = List.empty[PartialFunction[_, Future[TypeBus]]]
  var listOfServiceImplicitsReaders = Map.empty[String, ByteStreamReader[TypeBus] ]
  var listOfServiceImplicitsWriters = Map.empty[String, ByteStreamWriter[TypeBus] ]
  var listOfServiceFunctions = List.empty[(String, String)]

  /***
    * op - internal method to register a partial function
    * @param p - the partial function to register.
    * @param reader - ByteStreamReader that knows how to convert Array[Byte] to a type T
    * @param writer - ByteStreamWriter that knows how to convert a type U to Array[Byte]
    * @tparam T - The IN service request type
    * @tparam U - The OUT service request type
    * @return - Unit
    */
  protected[this] def op[T <: UserBaseType : ClassTag, U <: UserBaseType : ClassTag](p: PartialFunction[T, Future[U]])(implicit reader: ByteStreamReader[T], writer: ByteStreamWriter[U] )  = {
    val topic = scala.reflect.classTag[T].runtimeClass.getCanonicalName
    val returnType = scala.reflect.classTag[U].runtimeClass.getCanonicalName
    listOfFunctions = (topic, returnType) :: listOfFunctions
    listOfPartials = p :: listOfPartials
    listOfImplicitsReaders +=  (topic -> reader.asInstanceOf[ByteStreamReader[UserBaseType]])
    listOfImplicitsWriters +=  (returnType -> writer.asInstanceOf[ByteStreamWriter[UserBaseType]])
    println(s"partial: ${p} ${scala.reflect.classTag[T].runtimeClass.getCanonicalName}")
    Unit
  }

  /***
    * op2 - internal method to register a partial function that also receives EventMeta
    * @param p - the partial function to register.
    * @param reader - ByteStreamReader that knows how to convert Array[Byte] to a type T
    * @param writer - ByteStreamWriter that knows how to convert a type U to Array[Byte]
    * @tparam T - The IN service request type
    * @tparam U - The OUT service request type
    * @return - Unit
    */
  protected[this] def op2[T <: UserBaseType : ClassTag, U <: UserBaseType : ClassTag](p: PartialFunction[(T, EventMeta), Future[U]])(implicit reader: ByteStreamReader[T], writer: ByteStreamWriter[U] )  = {
    val topic = scala.reflect.classTag[T].runtimeClass.getCanonicalName
    val returnType = scala.reflect.classTag[U].runtimeClass.getCanonicalName
    listOfFunctions = (topic, returnType) :: listOfFunctions
    listOfPartialsWithMeta = p :: listOfPartialsWithMeta
    listOfImplicitsReaders +=  (topic -> reader.asInstanceOf[ByteStreamReader[UserBaseType]])
    listOfImplicitsWriters +=  (returnType -> writer.asInstanceOf[ByteStreamWriter[UserBaseType]])
    println(s"partial with meta: ${p} ${scala.reflect.classTag[T].runtimeClass.getCanonicalName}")
    Unit
  }

  /***
    * op2Unit - register a sink
    * @param p - the partial function to register.
    * @param reader - ByteStreamReader that knows how to convert Array[Byte] to a type T
    * @tparam T - The IN service request type
    * @return - Unit
    */
  protected[this] def op2Unit[T <: UserBaseType : ClassTag](p: PartialFunction[(T, EventMeta), Future[Unit] ])(implicit reader: ByteStreamReader[T] )  = {
    val topic = scala.reflect.classTag[T].runtimeClass.getCanonicalName
    listOfFunctions = (topic, "scala.Unit") :: listOfFunctions
    listOfPartialsWithMetaUnit = p :: listOfPartialsWithMetaUnit
    listOfImplicitsReaders +=  (topic -> reader.asInstanceOf[ByteStreamReader[UserBaseType]])
    println(s"partial with meta unit: ${p} ${scala.reflect.classTag[T].runtimeClass.getCanonicalName}")
    Unit
  }

  /***
    * op2Service - internal method to register HIDDEN TypeBus Service level functions
    * @param p - the partial function to register.
    * @param reader - ByteStreamReader that knows how to convert Array[Byte] to a type T
    * @param writer - ByteStreamWriter that knows how to convert a type U to Array[Byte]
    * @tparam T - The IN service request type
    * @tparam U - The OUT service request type
    * @return - Unit
    */
  protected[this] def op2Service[T <: TypeBus : ClassTag, U <: TypeBus : ClassTag](p: PartialFunction[(T, EventMeta), Future[U]])(implicit reader: ByteStreamReader[T], writer: ByteStreamWriter[U] )  = {
    val topic = scala.reflect.classTag[T].runtimeClass.getCanonicalName
    val returnType = scala.reflect.classTag[U].runtimeClass.getCanonicalName
    listOfServiceFunctions = (topic, returnType) :: listOfServiceFunctions
    listOfServicePartialsWithMeta = p :: listOfServicePartialsWithMeta
    listOfServiceImplicitsReaders +=  (topic -> reader.asInstanceOf[ByteStreamReader[TypeBus]])
    listOfServiceImplicitsWriters +=  (returnType -> writer.asInstanceOf[ByteStreamWriter[TypeBus]])
    println(s"partial with meta: ${p} ${scala.reflect.classTag[T].runtimeClass.getCanonicalName}")
    Unit
  }

  /***
    * funToPF - convert a function to a partial function
    * @param f - the function to convert
    * @tparam T - The IN service request type
    * @tparam S - The OUT service request type
    * @return - PartialFunction[T, Future[S]]
    */
  protected[this] def funToPF[T : ClassTag, S : ClassTag](f: (T) => Future[S]) = new PartialFunction[T, Future[S]] {
    def apply(x: T) = f(x.asInstanceOf[T])
    def isDefinedAt(x: T ) = x match{
      case _: T => true
      case _ => false
    }
  }

  /***
    * funToPF2 - convert a function to a partial function with EventMeta
    * @param f - the function to convert
    * @tparam T - The IN service request type
    * @tparam S - The OUT service request type
    * @return - PartialFunction[T, Future[S]]
    */
  protected[this] def funToPF2[T : ClassTag, S : ClassTag](f: (T, EventMeta) => Future[S]) = new PartialFunction[ (T,EventMeta), Future[S]] {
    def apply(x: (T, EventMeta) ) = f(x._1.asInstanceOf[T], x._2)
    def isDefinedAt(x: (T, EventMeta) ) = x._1 match{
      case _: T => true
      case _ => false
    }
  }

  /***
    * funToPF2Unit - convert a function to a partial function sink
    * @param f - the function to convert
    * @tparam T - The IN service request type
    * @tparam - Future[Unit] sink
    * @return - PartialFunction[ (T,EventMeta), Future[Unit]]
    */
  protected[this] def funToPF2Unit[T : ClassTag, Future[Unit]](f: (T, EventMeta) => Future[Unit]) = new PartialFunction[ (T,EventMeta), Future[Unit]] {
    def apply(x: (T, EventMeta) ) = f(x._1.asInstanceOf[T], x._2)
    def isDefinedAt(x: (T, EventMeta) ) = x._1 match{
      case _: T => true
      case _ => false
    }
  }

  protected[this] lazy val handleEvent = listOfPartials.asInstanceOf[List[PartialFunction[Any, Future[Any]]]].foldRight[PartialFunction[Any, Future[Any]] ](
    new PartialFunction[Any, Future[Any]] {
      def apply(x: Any) = throw new RuntimeException(s"Type not supported ${x.getClass.getCanonicalName}") // TODO: This needs to fail when we don't have the implicit
      def isDefinedAt(x: Any ) = true
    })( (a, b) => a.orElse(b) )

  protected[this] lazy val handleEventWithMeta = listOfPartialsWithMeta.asInstanceOf[List[PartialFunction[ (Any, EventMeta), Future[Any]]]].foldRight[PartialFunction[ (Any, EventMeta), Future[Any]] ](
    new PartialFunction[ (Any, EventMeta), Future[Any]] {
      def apply(x: (Any, EventMeta)) = throw new RuntimeException(s"Type not supported ${x._1.getClass.getCanonicalName}") // TODO: This needs to fail when we don't have the implicit
      def isDefinedAt(x: (Any, EventMeta) ) = false
    })( (a, b) => a.orElse(b) )

  protected[this] lazy val handleEventWithMetaUnit = listOfPartialsWithMetaUnit.asInstanceOf[List[PartialFunction[ (Any, EventMeta), Future[Unit]]]].foldRight[PartialFunction[ (Any, EventMeta), Future[Unit]] ](
    new PartialFunction[ (Any, EventMeta), Future[Unit]] {
      def apply(x: (Any, EventMeta)) = throw new RuntimeException(s"Type not supported ${x._1.getClass.getCanonicalName}") // TODO: This needs to fail when we don't have the implicit
      def isDefinedAt(x: (Any, EventMeta) ) = false
    })( (a, b) => a.orElse(b) )


  protected[this] lazy val handleServiceEventWithMeta = listOfServicePartialsWithMeta.asInstanceOf[List[PartialFunction[ (Any, EventMeta), Future[Any]]]].foldRight[PartialFunction[ (Any, EventMeta), Future[Any]] ](
    new PartialFunction[ (Any, EventMeta), Future[Any]] {
      def apply(x: (Any, EventMeta)) = throw new RuntimeException(s"Type not supported ${x._1.getClass.getCanonicalName}") // TODO: This needs to fail when we don't have the implicit
      def isDefinedAt(x: (Any, EventMeta) ) = false
    })( (a, b) => a.orElse(b) )



}
