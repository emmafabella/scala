/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package scala
package reflect

import java.lang.{ Class => jClass }

/**
 *
 * A `ClassTag[T]` stores the erased class of a given type `T`, accessible via the `runtimeClass`
 * field. This is particularly useful for instantiating `Array`s whose element types are unknown
 * at compile time.
 *
 * `ClassTag`s are a weaker special case of [[scala.reflect.api.TypeTags#TypeTag]]s, in that they
 * wrap only the runtime class of a given type, whereas a `TypeTag` contains all static type
 * information. That is, `ClassTag`s are constructed from knowing only the top-level class of a
 * type, without necessarily knowing all of its argument types. This runtime information is enough
 * for runtime `Array` creation.
 *
 * For example:
 * {{{
 *   scala> def mkArray[T : ClassTag](elems: T*) = Array[T](elems: _*)
 *   mkArray: [T](elems: T*)(implicit evidence\$1: scala.reflect.ClassTag[T])Array[T]
 *
 *   scala> mkArray(42, 13)
 *   res0: Array[Int] = Array(42, 13)
 *
 *   scala> mkArray("Japan","Brazil","Germany")
 *   res1: Array[String] = Array(Japan, Brazil, Germany)
 * }}}
 *
 * See [[scala.reflect.api.TypeTags]] for more examples, or the
 * [[http://docs.scala-lang.org/overviews/reflection/typetags-manifests.html Reflection Guide: TypeTags]]
 * for more details.
 *
 */
@scala.annotation.implicitNotFound(msg = "No ClassTag available for ${T}")
trait ClassTag[T] extends ClassManifestDeprecatedApis[T] with Equals with Serializable {
  // please, don't add any APIs here, like it was with `newWrappedArray` and `newArrayBuilder`
  // class tags, and all tags in general, should be as minimalistic as possible

  /** A class representing the type `U` to which `T` would be erased.
   *  Note that there is no subtyping relationship between `T` and `U`.
   */
  def runtimeClass: jClass[_]

  /** Produces a `ClassTag` that knows how to instantiate an `Array[Array[T]]` */
  def wrap: ClassTag[Array[T]] = ClassTag[Array[T]](arrayClass(runtimeClass))

  /** Produces a new array with element type `T` and length `len` */
  override def newArray(len: Int): Array[T] = {
    if (runtimeClass.isPrimitive) {
      runtimeClass match {
        case java.lang.Byte.TYPE      => new Array[Byte](len).asInstanceOf[Array[T]]
        case java.lang.Short.TYPE     => new Array[Short](len).asInstanceOf[Array[T]]
        case java.lang.Character.TYPE => new Array[Char](len).asInstanceOf[Array[T]]
        case java.lang.Integer.TYPE   => new Array[Int](len).asInstanceOf[Array[T]]
        case java.lang.Long.TYPE      => new Array[Long](len).asInstanceOf[Array[T]]
        case java.lang.Float.TYPE     => new Array[Float](len).asInstanceOf[Array[T]]
        case java.lang.Double.TYPE    => new Array[Double](len).asInstanceOf[Array[T]]
        case java.lang.Boolean.TYPE   => new Array[Boolean](len).asInstanceOf[Array[T]]
        case java.lang.Void.TYPE      => new Array[Unit](len).asInstanceOf[Array[T]]
      }
    } else {
      java.lang.reflect.Array.newInstance(runtimeClass, len).asInstanceOf[Array[T]]
    }
  }

  /** A ClassTag[T] can serve as an extractor that matches only objects of type T.
   *
   * The compiler tries to turn unchecked type tests in pattern matches into checked ones
   * by wrapping a `(_: T)` type pattern as `ct(_: T)`, where `ct` is the `ClassTag[T]` instance.
   * Type tests necessary before calling other extractors are treated similarly.
   * `SomeExtractor(...)` is turned into `ct(SomeExtractor(...))` if `T` in `SomeExtractor.unapply(x: T)`
   * is uncheckable, but we have an instance of `ClassTag[T]`.
   */
  def unapply(x: Any): Option[T] =
    if (runtimeClass.isInstance(x)) Some(x.asInstanceOf[T])
    else None

  // case class accessories
  override def canEqual(x: Any) = x.isInstanceOf[ClassTag[_]]
  override def equals(x: Any) = x.isInstanceOf[ClassTag[_]] && this.runtimeClass == x.asInstanceOf[ClassTag[_]].runtimeClass
  override def hashCode = runtimeClass.##
  override def toString = {
    def prettyprint(clazz: jClass[_]): String =
      if (clazz.isArray) s"Array[${prettyprint(clazz.getComponentType)}]" else
      clazz.getName
    prettyprint(runtimeClass)
  }
}

/**
 * Class tags corresponding to primitive types and constructor/extractor for ClassTags.
 */
object ClassTag {
  private val ObjectTYPE = classOf[java.lang.Object]
  private val NothingTYPE = classOf[scala.runtime.Nothing$]
  private val NullTYPE = classOf[scala.runtime.Null$]

  val Byte    : ClassTag[scala.Byte]       = Manifest.Byte
  val Short   : ClassTag[scala.Short]      = Manifest.Short
  val Char    : ClassTag[scala.Char]       = Manifest.Char
  val Int     : ClassTag[scala.Int]        = Manifest.Int
  val Long    : ClassTag[scala.Long]       = Manifest.Long
  val Float   : ClassTag[scala.Float]      = Manifest.Float
  val Double  : ClassTag[scala.Double]     = Manifest.Double
  val Boolean : ClassTag[scala.Boolean]    = Manifest.Boolean
  val Unit    : ClassTag[scala.Unit]       = Manifest.Unit
  val Any     : ClassTag[scala.Any]        = Manifest.Any
  val Object  : ClassTag[java.lang.Object] = Manifest.Object
  val AnyVal  : ClassTag[scala.AnyVal]     = Manifest.AnyVal
  val AnyRef  : ClassTag[scala.AnyRef]     = Manifest.AnyRef
  val Nothing : ClassTag[scala.Nothing]    = Manifest.Nothing
  val Null    : ClassTag[scala.Null]       = Manifest.Null

  private[this] val cache = new ClassValue[ClassTag[_]] {
    override def computeValue(runtimeClass: jClass[_]): ClassTag[_] = {
      runtimeClass match {
        case x if x.isPrimitive => primitiveClassTag(runtimeClass)
        case ObjectTYPE         => ClassTag.Object
        case NothingTYPE        => ClassTag.Nothing
        case NullTYPE           => ClassTag.Null
        case _                  => new GenericClassTag[AnyRef](runtimeClass)
      }
    }

    private def primitiveClassTag[T](runtimeClass: Class[_]): ClassTag[_] = runtimeClass match {
      case java.lang.Byte.TYPE      => ClassTag.Byte
      case java.lang.Short.TYPE     => ClassTag.Short
      case java.lang.Character.TYPE => ClassTag.Char
      case java.lang.Integer.TYPE   => ClassTag.Int
      case java.lang.Long.TYPE      => ClassTag.Long
      case java.lang.Float.TYPE     => ClassTag.Float
      case java.lang.Double.TYPE    => ClassTag.Double
      case java.lang.Boolean.TYPE   => ClassTag.Boolean
      case java.lang.Void.TYPE      => ClassTag.Unit
    }
  }

  @SerialVersionUID(1L)
  private class GenericClassTag[T](val runtimeClass: jClass[_]) extends ClassTag[T] {
    override def newArray(len: Int): Array[T] = {
      java.lang.reflect.Array.newInstance(runtimeClass, len).asInstanceOf[Array[T]]
    }
  }

  def apply[T](runtimeClass1: jClass[_]): ClassTag[T] = cache.get(runtimeClass1).asInstanceOf[ClassTag[T]]

  def unapply[T](ctag: ClassTag[T]): Option[Class[_]] = Some(ctag.runtimeClass)
}
