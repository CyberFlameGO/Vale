package net.verdagon.vale

// A condition that reflects a user error.
object vcheck {
  def apply[T <: Throwable](condition: Boolean, message: String): Unit = {
    vcheck(condition, message, (s) => new RuntimeException(s))
  }
  def apply[T <: Throwable](condition: Boolean, exceptionMaker: (String) => T): Unit = {
    vcheck(condition, "Check failed!", exceptionMaker)
  }
  def apply[T <: Throwable](condition: Boolean, exceptionMaker: () => Throwable): Unit = {
    if (!condition) {
      throw exceptionMaker()
    }
  }
  def apply[T <: Throwable](condition: Boolean, message: String, exceptionMaker: (String) => T): Unit = {
    if (!condition) {
      throw exceptionMaker(message)
    }
  }
}

// A condition that reflects a programmer error.
object vassert {
  def apply(condition: Boolean): Unit = {
    vassert(condition, "Assertion failed!")
  }
  def apply(condition: Boolean, message: String): Unit = {
    if (!condition) {
      vfail(message)
    }
  }
}

object vcurious {
  def apply(): Nothing = {
    vfail()
  }
  def apply(message: String): Nothing = {
    vfail(message)
  }
  def apply(condition: Boolean): Unit = {
    vassert(condition)
  }
  def apply(condition: Boolean, message: String): Unit = {
    vassert(condition, message)
  }
}


object vassertSome {
  def apply[T](thing: Option[T], message: String): T = {
    thing match {
      case None => vfail(message)
      case Some(x) => x
    }
  }
  def apply[T](thing: Option[T]): T = {
    apply(thing, "Expected non-empty!")
  }
}

object vassertOne {
  def apply[T](thing: Vector[T], message: String): T = {
    thing match {
      case Vector(x) => x
      case _ => vfail(message)
    }
  }
  def apply[T](thing: Vector[T]): T = {
    apply(thing, "Expected exactly one element!")
  }
}

object vfail {
  def apply(message: Object): Nothing = {
    throw new RuntimeException(message.toString)
  }
  def apply(): Nothing = {
    vfail("fail!")
  }
}

object vwat {
  def apply(): Nothing = {
    vfail("wat")
  }
  def apply(message: String): Nothing = {
    vfail("wat: " + message)
  }
}

object vimpl {
  def apply(): Nothing = {
    vfail("impl")
  }
  def apply(message: String): Nothing = {
    vfail(message)
  }

  def unapply(thing: Any): Option[Nothing] = {
    vimpl()
  }
}
