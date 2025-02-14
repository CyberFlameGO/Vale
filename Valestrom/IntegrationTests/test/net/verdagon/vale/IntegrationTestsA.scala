package net.verdagon.vale

import net.verdagon.vale.astronomer.{ICompileErrorA, ProgramA}

import java.io.FileNotFoundException
import net.verdagon.vale.templar._
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.vivem.{ConstraintViolatedException, Heap, IntV, PrimitiveKindV, ReferenceV, StructInstanceV, Vivem}
import net.verdagon.von.{IVonData, VonBool, VonFloat, VonInt, VonObject}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.{FullCompilation, FullCompilationOptions}
import net.verdagon.vale.hammer.VonHammer
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.{FullNameH, IntH, ProgramH, PrototypeH, ReadonlyH, ReadwriteH, YonderH}
import net.verdagon.vale.parser.{FailedParse, FileP}
import net.verdagon.vale.scout.{ICompileErrorS, ProgramS}
import net.verdagon.vale.templar.templata.SignatureT
import net.verdagon.vale.templar.types.{CoordT, IntT, ReadonlyT, ShareT, StrT}

import scala.collection.immutable.List


object RunCompilation {
  def test(code: String*): RunCompilation = {
    new RunCompilation(
      Vector(
        PackageCoordinate.BUILTIN,
        PackageCoordinate.TEST_TLD),
      Builtins.getCodeMap()
        .or(FileCoordinateMap.test(code.toVector))
        .or(Tests.getPackageToResourceResolver),
      FullCompilationOptions())
  }
}

class RunCompilation(
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]],
  options: FullCompilationOptions = FullCompilationOptions()) {
  var fullCompilation = new FullCompilation(packagesToBuild, packageToContentsResolver, options)

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = fullCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = fullCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = fullCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = fullCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = fullCompilation.getAstrouts()
  def getTemputs(): Result[Hinputs, ICompileErrorT] = fullCompilation.getTemputs()
  def expectTemputs(): Hinputs = fullCompilation.expectTemputs()
  def getHamuts(): ProgramH = fullCompilation.getHamuts()

  def evalForKind(heap: Heap, args: Vector[ReferenceV]): IVonData = {
    Vivem.executeWithHeap(getHamuts(), heap, args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def run(heap: Heap, args: Vector[ReferenceV]): Unit = {
    Vivem.executeWithHeap(getHamuts(), heap, args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def run(args: Vector[PrimitiveKindV]): Unit = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def evalForKind(args: Vector[PrimitiveKindV]): IVonData = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def evalForKind(
    args: Vector[PrimitiveKindV],
    stdin: Vector[String]):
  IVonData = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.stdinFromList(stdin), Vivem.regularStdout)
  }
  def evalForStdout(args: Vector[PrimitiveKindV]): String = {
    val (stdoutStringBuilder, stdoutFunc) = Vivem.stdoutCollector()
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, stdoutFunc)
    stdoutStringBuilder.mkString
  }
  def evalForKindAndStdout(args: Vector[PrimitiveKindV]): (IVonData, String) = {
    val (stdoutStringBuilder, stdoutFunc) = Vivem.stdoutCollector()
    val kind = Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, stdoutFunc)
    (kind, stdoutStringBuilder.mkString)
  }
}

class IntegrationTestsA extends FunSuite with Matchers {
//  test("Scratch scratch") {
//    val compile =
//      RunCompilation.test(
//        """
//          |scratch code here
//          |""".stripMargin)
//    compile.evalForKind(Vector())
//  }

  test("Simple program returning an int") {
    val compile = RunCompilation.test("fn main() int export {3}")
    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("Hardcoding negative numbers") {
    val compile = RunCompilation.test("fn main() int export {-3}")
    compile.evalForKind(Vector()) shouldEqual VonInt(-3)
  }

  test("Taking an argument and returning it") {
    val compile = RunCompilation.test("fn main(a int) export int {a}")
    compile.evalForKind(Vector(IntV(5, 32))) shouldEqual VonInt(5)
  }

  test("Tests adding two numbers") {
    val compile = RunCompilation.test("fn main() int export { +(2, 3) }")
    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Tests adding two floats") {
    val compile = RunCompilation.test("fn main() float export { +(2.5, 3.5) }")
    compile.evalForKind(Vector()) shouldEqual VonFloat(6.0f)
  }

  test("Tests inline adding") {
    val compile = RunCompilation.test("fn main() int export { 2 + 3 }")
    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Test constraint ref") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/constraintRef.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(8)
  }

  test("Tests inline adding more") {
    val compile = RunCompilation.test("fn main() int export { 2 + 3 + 4 + 5 + 6 }")
    compile.evalForKind(Vector()) shouldEqual VonInt(20)
  }

  test("Simple lambda") {
    val compile = RunCompilation.test("fn main() int export {{7}()}")
    compile.evalForKind(Vector()) shouldEqual VonInt(7)
  }

  test("Lambda with one magic arg") {
    val compile = RunCompilation.test("fn main() int export {{_}(3)}")
    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }


  // Test that the lambda's arg is the right type, and the name is right
  test("Lambda with a type specified param") {
    val compile = RunCompilation.test("fn main() int export {(a int){ +(a,a)}(3)}");
    compile.evalForKind(Vector()) shouldEqual VonInt(6)
  }

  test("Test overloads") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/functions/overloads.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(6)
  }

  test("Test block") {
    val compile = RunCompilation.test("fn main() int export {true; 200; = 300;}")
    compile.evalForKind(Vector()) shouldEqual VonInt(300)
  }

  test("Test templates") {
    val compile = RunCompilation.test(
      """
        |fn ~<T>(a T, b T) T { a }
        |fn main() int export {true ~ false; 2 ~ 2; = 3 ~ 3;}
      """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("Test mutating a local var") {
    val compile = RunCompilation.test("fn main() export {a! = 3; set a = 4; }")
    compile.run(Vector())
  }

  test("Test returning a local mutable var") {
    val compile = RunCompilation.test("fn main() int export {a! = 3; set a = 4; = a;}")
    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }

  test("Test taking a callable param") {
    val compile = RunCompilation.test(
      """
        |fn do(callable) infer-ret {callable()}
        |fn main() int export {do({ 3 })}
      """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("Stamps an interface template via a function parameter") {
    val compile = RunCompilation.test(
      """
        |interface MyInterface<T> rules(T Ref) { }
        |fn doAThing<T>(i MyInterface<T>) { }
        |
        |struct SomeStruct<T> rules(T Ref) { }
        |fn doAThing<T>(s SomeStruct<T>) { }
        |impl<T> MyInterface<T> for SomeStruct<T>;
        |
        |export MyInterface<int> as SomeIntInterface;
        |export SomeStruct<int> as SomeIntStruct;
        |
        |fn main(a SomeStruct<int>) export {
        |  doAThing<int>(a);
        |}
      """.stripMargin)
    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD)
    val heap = new Heap(System.out)
    val ref =
      heap.add(m.OwnH, YonderH, ReadwriteH, StructInstanceV(
        packageH.lookupStruct("SomeStruct"),
        Some(Vector())))
    compile.run(heap, Vector(ref))
  }

  test("Tests unstackifying a variable multiple times in a function") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/multiUnstackify.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Reads a struct member") {
    val compile = RunCompilation.test(
      """
        |struct MyStruct { a int; }
        |fn main() int export { ms = MyStruct(7); = ms.a; }
      """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(7)
  }

  test("Add two i64") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/add64ret.vale"))
    val temputs = compile.getTemputs()
    val hamuts = compile.getHamuts()
    compile.evalForKind(Vector()) shouldEqual VonInt(42L)
  }

  test("=== true") {
    val compile = RunCompilation.test(
      """
        |struct MyStruct { a int; }
        |fn main() bool export {
        |  a = MyStruct(7);
        |  = &a === &a;
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonBool(true)
  }

  test("=== false") {
    val compile = RunCompilation.test(
      """
        |struct MyStruct { a int; }
        |fn main() bool export {
        |  a = MyStruct(7);
        |  b = MyStruct(7);
        |  = &a === &b;
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonBool(false)
  }

  test("set swapping locals") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/mutswaplocals.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("imm tuple access") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/tuples/immtupleaccess.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  // Known failure 2020-08-20
  // The reason this isnt working:
  // The InterfaceCall2 instruction is only ever created as part of an abstract function's body.
  // (Yes, abstract functions have a body, specifically only containing an InterfaceCall2 on the param)
  // So, if there's *already* a body there, we won't be making the InterfaceCall2 instruction.
  // Short term, let's disallow default implementations.
//  test("Tests virtual doesn't get called if theres a better override") {
//    val compile = RunCompilation.test(
//      """
//        |interface MyOption { }
//        |
//        |struct MySome {
//        |  value MyList;
//        |}
//        |impl MyOption for MySome;
//        |
//        |struct MyNone { }
//        |impl MyOption for MyNone;
//        |
//        |
//        |struct MyList {
//        |  value int;
//        |  next MyOption;
//        |}
//        |
//        |fn sum(list &MyList) int {
//        |  list.value + sum(list.next)
//        |}
//        |
//        |fn sum(virtual opt &MyOption) int { panic("called virtual sum!") }
//        |fn sum(opt &MyNone impl MyOption) int { 0 }
//        |fn sum(opt &MySome impl MyOption) int {
//        |   sum(opt.value)
//        |}
//        |
//        |
//        |fn main() int export {
//        |  list = MyList(10, MySome(MyList(20, MySome(MyList(30, MyNone())))));
//        |  = sum(&list);
//        |}
//        |
//        |""".stripMargin)
//    val hamuts = compile.getHamuts();
//    compile.evalForKind(Vector()) shouldEqual VonInt(60)
//  }

  test("Tests single expression and single statement functions' returns") {
    val compile = RunCompilation.test(
      """
        |struct MyThing { value int; }
        |fn moo() MyThing { MyThing(4) }
        |fn main() export { moo(); }
      """.stripMargin)
    compile.run(Vector())
  }

  test("Tests calling a templated struct's constructor") {
    val compile = RunCompilation.test(
      """
        |struct MySome<T> rules(T Ref) { value T; }
        |fn main() int export {
        |  MySome<int>(4).value
        |}
      """.stripMargin)
    compile.evalForKind(Vector())
  }

  test("Test int generic") {
    val compile = RunCompilation.test(
      """
        |
        |struct Vec<N, T> rules(N int)
        |{
        |  values [<imm> N * T];
        |}
        |
        |fn main() int export {
        |  v = Vec<3, int>([imm][3, 4, 5]);
        |  = v.values.2;
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/upcasting.vale"))
    compile.run(Vector())
  }

  test("Tests upcasting from if") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/if/upcastif.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Tests from file") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/lambdas/doubleclosure.vale"))
    compile.run(Vector())
  }

  test("Tests from subdir file") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/round.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(8)
  }

  test("Tests calling a virtual function") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/calling.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(7)
  }

  test("Tests making a variable with a pattern") {
    // Tests putting MyOption<int> as the type of x.
    val compile = RunCompilation.test(
      """
        |interface MyOption<T> rules(T Ref) { }
        |
        |struct MySome<T> rules(T Ref) {}
        |impl<T> MyOption<T> for MySome<T>;
        |
        |fn doSomething(opt MyOption<int>) int {
        |  = 9;
        |}
        |
        |fn main() int export {
        |	x MyOption<int> = MySome<int>();
        |	= doSomething(x);
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }


  test("Tests a linked list") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/virtuals/ordinarylinkedlist.vale"))
    compile.evalForKind(Vector())
  }

  test("Tests a templated linked list") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/templatedlinkedlist.vale"))
    compile.evalForKind(Vector())
  }

  test("Tests calling an abstract function") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/genericvirtuals/callingAbstract.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }

  test("Template overrides are stamped") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/templatedoption.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(1)
  }

  test("Tests a foreach for a linked list") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/foreachlinkedlist.vale"))
    compile.evalForStdout(Vector()) shouldEqual "102030"
  }

  // When we call a function with a virtual parameter, try stamping for all ancestors in its
  // place.
  // We're stamping all ancestors, and all ancestors have virtual.
  // Virtual starts a function family.
  // So, this checks that it and its three ancestors are all stamped and all get their own
  // function families.
  test("Stamp multiple ancestors") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/genericvirtuals/stampMultipleAncestors.vale"))
    val temputs = compile.expectTemputs()
    compile.evalForKind(Vector())
  }

  test("Tests recursion") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/functions/recursion.vale"))
    compile.evalForKind(Vector()) shouldEqual VonInt(120)
  }

  test("Tests floats") {
    val compile = RunCompilation.test(
      """
        |struct Moo imm {
        |  x float;
        |}
        |fn main() int export {
        |  7
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(7)
  }

  test("getOr function") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/genericvirtuals/getOr.vale"))

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("Function return without ret upcasts") {
    val compile = RunCompilation.test(
      """
        |interface XOpt { }
        |struct XSome { value int; }
        |impl XOpt for XSome;
        |
        |fn doIt() XOpt {
        |  XSome(9)
        |}
        |
        |fn main() int export {
        |  a = doIt();
        |  = 3;
        |}
        |""".stripMargin)

    val temputs = compile.expectTemputs()
    val doIt = temputs.lookupFunction("doIt")
    doIt.only({
      case StructToInterfaceUpcastTE(_, _) =>
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("Function return with ret upcasts") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/retUpcast.vale"))

    val temputs = compile.expectTemputs()
    val doIt = temputs.lookupFunction("doIt")
    doIt.only({
      case StructToInterfaceUpcastTE(_, _) =>
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("Map function") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/mapFunc.vale"))
    compile.expectTemputs()

    compile.evalForKind(Vector()) shouldEqual VonBool(true)
  }

  test("Test shaking") {
    // Make sure that functions that cant be called by main will not be included.

    val compile = RunCompilation.test(
      """import printutils.*;
        |fn bork(x str) { print(x); }
        |fn helperFunc(x int) { print(x); }
        |fn helperFunc(x str) { print(x); }
        |fn main() export {
        |  helperFunc(4);
        |}
        |""".stripMargin)
    val hinputs = compile.expectTemputs()

    vassertSome(hinputs.lookupFunction(SignatureT(FullNameT(PackageCoordinate.TEST_TLD, Vector.empty, FunctionNameT("helperFunc", Vector.empty, Vector(CoordT(ShareT, ReadonlyT, IntT.i32)))))))

    vassert(None == hinputs.lookupFunction(SignatureT(FullNameT(PackageCoordinate.TEST_TLD, Vector.empty, FunctionNameT("bork", Vector.empty, Vector(CoordT(ShareT, ReadonlyT, StrT())))))))

    vassert(None == hinputs.lookupFunction(SignatureT(FullNameT(PackageCoordinate.TEST_TLD, Vector.empty, FunctionNameT("helperFunc", Vector.empty, Vector(CoordT(ShareT, ReadonlyT, StrT())))))))
  }

//  test("Test overloading between borrow and own") {
//    val compile = FullCompilation
//      """
//        |interface IMoo {}
//        |struct Moo {}
//        |impl IMoo for Moo;
//        |
//        |fn func(virtual moo IMoo) int abstract;
//        |fn func(virtual moo &IMoo) int abstract;
//        |
//        |fn func(moo Moo impl IMoo) int { 73 }
//        |fn func(moo &Moo impl IMoo) int { 42 }
//        |
//        |fn main() int export {
//        |  func(&Moo())
//        |}
//        |""".stripMargin)
//    val temputs = compile.getTemputs()
//
//    compile.evalForKind(Vector()) shouldEqual VonInt(42)
//  }

  test("Test returning empty seq") {
    val compile = RunCompilation.test(
      """fn main() [] export {
        |  []
        |}
        |""".stripMargin)
    val temputs = compile.expectTemputs()

    compile.run(Vector())
  }

  test("Test export functions") {
    val compile = RunCompilation.test(
      """fn moo() int export {
        |  42
        |}
        |""".stripMargin)
    val hamuts = compile.getHamuts()
    VonHammer.vonifyProgram(hamuts)
  }

  test("Test extern functions") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/externs/extern.vale"))

    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate("math", Vector.empty))

    // The extern we make should have the name we expect
    vassertSome(packageH.externNameToFunction.get("sqrt")) match {
      case PrototypeH(FullNameH("sqrt",_,PackageCoordinate("math",Vector()),_),_,_) =>
    }

    // We also made an internal function that contains an extern call
    val externSqrt = packageH.lookupFunction("sqrt")
    vassert(externSqrt.isExtern)

    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }

  test("Test narrowing between borrow and owning overloads") {
    // See NMORFI for why this test is here. Before the SCCTT fix, it couldn't resolve between the two
    // `get` overloads, because the borrow ownership (from the opt.get()) was creeping into the rules
    // too far.

    val compile = RunCompilation.test(
      """
        |import panicutils.*;
        |
        |interface XOpt<T> rules(T Ref) { }
        |struct XNone<T> rules(T Ref) { }
        |impl<T> XOpt<T> for XNone<T>;
        |
        |fn get<T>(virtual opt XOpt<T>) int abstract;
        |fn get<T>(opt XNone<T> impl XOpt<T>) int { __vbi_panic() }
        |
        |fn get<T>(virtual opt &XOpt<T>) int abstract;
        |fn get<T>(opt &XNone<T> impl XOpt<T>) int { 42 }
        |
        |fn main() int export {
        |  opt XOpt<int> = XNone<int>();
        |  = opt.get();
        |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Test catch deref after drop") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/invalidaccess.vale"))
    try {
      compile.evalForKind(Vector()) shouldEqual VonInt(42)
      vfail()
    } catch {
      case ConstraintViolatedException(_) => // good!
    }
  }

  // This test is here because we had a bug where the compiler was enforcing that we unstackify
  // the same locals from all branches of if, even if they were constraint refs.
  test("Using same constraint ref from both branches of if") {
    val compile = RunCompilation.test(
      """
        |struct Moo {}
        |fn foo(a &Moo) int { 41 }
        |fn bork(a &Moo) int {
        |  if (false) {
        |    ret foo(a);
        |  } else if (false) {
        |    ret foo(a);
        |  } else {
        |    // continue
        |  }
        |  ret foo(a) + 1;
        |}
        |fn main() int export {
        |  ret bork(&Moo());
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }


  // Compiler should be fine with moving things from if statements if we ret out.
  test("Moving same thing from both branches of if") {
    val compile = RunCompilation.test(
      """
        |struct Moo {}
        |fn foo(a Moo) int { 41 }
        |fn bork(a Moo) int {
        |  if (false) {
        |    ret foo(a);
        |  } else if (false) {
        |    ret foo(a);
        |  } else {
        |    // continue
        |  }
        |  ret 42;
        |}
        |fn main() int export {
        |  ret bork(Moo());
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("exporting array") {
    val compilation = RunCompilation.test("export Array<mut, vary, int> as IntArray;")
    val hamuts = compilation.getHamuts()
    val testPackage = hamuts.lookupPackage(PackageCoordinate.TEST_TLD)
    val kindH = vassertSome(testPackage.exportNameToKind.get("IntArray"))

    val builtinPackage = hamuts.lookupPackage(PackageCoordinate.BUILTIN)
    val rsa = vassertSome(builtinPackage.runtimeSizedArrays.find(_.kind == kindH))
    rsa.rawArray.elementType.kind shouldEqual IntH.i32
  }
}
