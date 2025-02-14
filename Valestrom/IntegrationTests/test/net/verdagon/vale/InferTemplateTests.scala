package net.verdagon.vale

import net.verdagon.vale.templar.{CitizenNameT, CodeVarNameT, FullNameT, simpleName}
import net.verdagon.vale.templar.templata.{CoordTemplata, ParameterT}
import net.verdagon.vale.templar.types.{ConstraintT, CoordT, OwnT, ReadonlyT, ReadwriteT, StructTT}
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class InferTemplateTests extends FunSuite with Matchers {
  test("Test inferring a borrowed argument") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |fn moo<T>(m &T) int { m.hp }
        |fn main() int export {
        |  x = Muta(10);
        |  = moo(&x);
        |}
      """.stripMargin)

    val moo = compile.expectTemputs().lookupFunction("moo")
    moo.header.params match {
      case Vector(ParameterT(CodeVarNameT("m"), _, CoordT(ConstraintT,ReadonlyT, _))) =>
    }
    moo.header.fullName.last.templateArgs shouldEqual
      Vector(CoordTemplata(CoordT(OwnT,ReadwriteT,StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector.empty,CitizenNameT("Muta",Vector.empty))))), CoordTemplata(CoordT(ConstraintT,ReadonlyT,StructTT(FullNameT(PackageCoordinate.TEST_TLD, Vector.empty,CitizenNameT("Muta",Vector.empty))))))

    compile.evalForKind(Vector()) shouldEqual VonInt(10)
  }
  test("Test inferring a borrowed static sized array") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |fn moo<N>(m &[N * Muta]) int { m[0].hp }
        |fn main() int export {
        |  x = [][Muta(10)];
        |  = moo(&x);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(10)
  }
  test("Test inferring an owning static sized array") {
    val compile = RunCompilation.test(
      """
        |struct Muta { hp int; }
        |fn moo<N>(m [N * Muta]) int { m[0].hp }
        |fn main() int export {
        |  x = [][Muta(10)];
        |  = moo(x);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(10)
  }
}
