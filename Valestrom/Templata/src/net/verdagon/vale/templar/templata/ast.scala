package net.verdagon.vale.templar.templata


import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.{FullNameT, FunctionNameT, IFunctionNameT, IVarNameT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{FileCoordinate, PackageCoordinate, vassert, vassertSome, vfail, vimpl}

trait QueriableT {
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T];

  def allOf[T](classs: Class[T]): Vector[T] = {
    all({
      case x if classs.isInstance(x) => classs.cast(x)
    })
  }

  def only[T](func: PartialFunction[QueriableT, T]): T = {
    val list = all(func)
    if (list.size > 1) {
      vfail("More than one!");
    } else if (list.isEmpty) {
      vfail("Not found!");
    }
    list.head
  }

  def onlyOf[T](classs: Class[T]): T = {
    val list =
      all({
        case x if classs.isInstance(x) => classs.cast(x)
      })
    if (list.size > 1) {
      vfail("More than one!");
    } else if (list.isEmpty) {
      vfail("Not found!");
    }
    list.head
  }
}

trait VirtualityT extends QueriableT {
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T];
}
case object AbstractT$ extends VirtualityT {
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}
case class OverrideT(interface: InterfaceTT) extends VirtualityT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ interface.all(func)
  }
}

case class ParameterT(
    name: IVarNameT,
    virtuality: Option[VirtualityT],
    tyype: CoordT) extends QueriableT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ virtuality.toVector.flatMap(_.all(func)) ++ tyype.all(func)
  }
}

sealed trait IPotentialBanner {
  def banner: FunctionBannerT
}

case class PotentialBannerFromFunctionS(
  banner: FunctionBannerT,
  function: FunctionTemplata
) extends IPotentialBanner { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class PotentialBannerFromExternFunction(
  header: FunctionHeaderT
) extends IPotentialBanner {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def banner: FunctionBannerT = header.toBanner
}

// A "signature" is just the things required for overload resolution, IOW function name and arg types.

// An autograph could be a super signature; a signature plus attributes like virtual and mutable.
// If we ever need it, a "schema" could be something.

// A FunctionBanner2 is everything in a FunctionHeader2 minus the return type.
// These are only made by the FunctionTemplar, to signal that it's currently being
// evaluated or it's already been evaluated.
// It's easy to see all possible function banners, but not easy to see all possible
// function headers, because functions don't have to specify their return types and
// it takes a complete templar evaluate to deduce a function's return type.

case class SignatureT(fullName: FullNameT[IFunctionNameT]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def paramTypes: Vector[CoordT] = fullName.last.parameters
}

case class FunctionBannerT(
    originFunction: Option[FunctionA],
    fullName: FullNameT[IFunctionNameT],
    params: Vector[ParameterT]) extends QueriableT  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  vassert(fullName.last.parameters == params.map(_.tyype))

  def toSignature: SignatureT = SignatureT(fullName)
  def paramTypes: Vector[CoordT] = params.map(_.tyype)

  def getAbstractInterface: Option[InterfaceTT] = {
    val abstractInterfaces =
      params.collect({
        case ParameterT(_, Some(AbstractT$), CoordT(_, _, ir @ InterfaceTT(_))) => ir
      })
    vassert(abstractInterfaces.size <= 1)
    abstractInterfaces.headOption
  }

  def getOverride: Option[(StructTT, InterfaceTT)] = {
    val overrides =
      params.collect({
        case ParameterT(_, Some(OverrideT(ir)), CoordT(_, _, sr @ StructTT(_))) => (sr, ir)
      })
    vassert(overrides.size <= 1)
    overrides.headOption
  }

  def getVirtualIndex: Option[Int] = {
    val indices =
      params.zipWithIndex.collect({
        case (ParameterT(_, Some(OverrideT(_)), _), index) => index
        case (ParameterT(_, Some(AbstractT$), _), index) => index
      })
    vassert(indices.size <= 1)
    indices.headOption
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ params.flatMap(_.all(func))
  }

  def unapply(arg: FunctionBannerT):
  Option[(FullNameT[IFunctionNameT], Vector[ParameterT])] =
    Some(fullName, params)

  override def toString: String = {
    // # is to signal that we override this
    "FunctionBanner2#(" + fullName + ", " + params + ")"
  }
}

sealed trait IFunctionAttribute2
sealed trait ICitizenAttribute2
case class Extern2(packageCoord: PackageCoordinate) extends IFunctionAttribute2 with ICitizenAttribute2 { // For optimization later
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
// There's no Export2 here, we use separate KindExport and FunctionExport constructs.
//case class Export2(packageCoord: PackageCoordinate) extends IFunctionAttribute2 with ICitizenAttribute2
case object Pure2 extends IFunctionAttribute2 with ICitizenAttribute2
case object UserFunction2 extends IFunctionAttribute2 // Whether it was written by a human. Mostly for tests right now.

case class FunctionHeaderT(
    fullName: FullNameT[IFunctionNameT],
    attributes: Vector[IFunctionAttribute2],
    params: Vector[ParameterT],
    returnType: CoordT,
    maybeOriginFunction: Option[FunctionA]) extends QueriableT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  // Make sure there's no duplicate names
  vassert(params.map(_.name).toSet.size == params.size);

  vassert(fullName.last.parameters == paramTypes)

  def isExtern = attributes.exists({ case Extern2(_) => true case _ => false })
//  def isExport = attributes.exists({ case Export2(_) => true case _ => false })
  def isUserFunction = attributes.contains(UserFunction2)
  def getAbstractInterface: Option[InterfaceTT] = toBanner.getAbstractInterface
  def getOverride: Option[(StructTT, InterfaceTT)] = toBanner.getOverride
  def getVirtualIndex: Option[Int] = toBanner.getVirtualIndex

  maybeOriginFunction.foreach(originFunction => {
    if (originFunction.identifyingRunes.size != fullName.last.templateArgs.size) {
      vfail("wtf m8")
    }
  })

  def toBanner: FunctionBannerT = FunctionBannerT(maybeOriginFunction, fullName, params)
  def toPrototype: PrototypeT = PrototypeT(fullName, returnType)
  def toSignature: SignatureT = toPrototype.toSignature

  def paramTypes: Vector[CoordT] = params.map(_.tyype)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ params.flatMap(_.all(func)) ++ returnType.all(func)
  }

  def unapply(arg: FunctionHeaderT): Option[(FullNameT[IFunctionNameT], Vector[ParameterT], CoordT)] =
    Some(fullName, params, returnType)
}

case class PrototypeT(
    fullName: FullNameT[IFunctionNameT],
    returnType: CoordT) extends QueriableT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def paramTypes: Vector[CoordT] = fullName.last.parameters
  def toSignature: SignatureT = SignatureT(fullName)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ paramTypes.flatMap(_.all(func)) ++ returnType.all(func)
  }
}

case class CodeLocationT(
  file: FileCoordinate,
  offset: Int
) extends QueriableT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }

  override def toString: String = file + ":" + offset
}

object CodeLocationT {
  // Keep in sync with CodeLocationS
  val zero = CodeLocationT.internal(-1)
  def internal(internalNum: Int): CodeLocationT = {
    vassert(internalNum < 0)
    CodeLocationT(FileCoordinate("", Vector.empty, "internal"), internalNum)
  }
}