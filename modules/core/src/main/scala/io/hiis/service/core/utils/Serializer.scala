package io.hiis.service.core.utils

import java.io.{
  ByteArrayInputStream,
  ByteArrayOutputStream,
  ObjectInputStream,
  ObjectOutputStream
}
import scala.util.Try

object Serializer {

  // Todo use apache commons
  def serialize[T](obj: T): Either[Throwable, Array[Byte]] = {
    val byteOut = new ByteArrayOutputStream()
    val objOut  = new ObjectOutputStream(byteOut)
    Try {
      objOut.writeObject(obj)
      objOut.close()
      byteOut.close()
      byteOut.toByteArray
    }.toEither
  }

  def deserialize[T](bytes: Array[Byte]): Either[Throwable, T] = {
    val byteIn = new ByteArrayInputStream(bytes)
    val objIn  = new ObjectInputStream(byteIn)
    Try {
      val obj = objIn.readObject().asInstanceOf[T]
      byteIn.close()
      objIn.close()
      obj
    }.toEither
  }
}
