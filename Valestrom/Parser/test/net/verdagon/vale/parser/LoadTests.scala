package net.verdagon.vale.parser

import net.liftweb.json._
import net.verdagon.vale.{Collector, vassert}
import net.verdagon.von.{JsonSyntax, VonPrinter}
import org.scalatest.{FunSuite, Matchers}

import java.nio.charset.Charset


class LoadTests extends FunSuite with Matchers with Collector {
  private def compileProgramWithComments(code: String): FileP = {
    Parser.runParserForProgramAndCommentRanges(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(result) => result._1
    }
  }
  private def compileProgram(code: String): FileP = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(result) => result
    }
  }

  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    CombinatorParsers.parse(parser, code.strip().toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail("Couldn't parse!\n" + input.pos.longString);
      }
      case CombinatorParsers.Success(expr, rest) => {
        vassert(rest.atEnd)
        expr
      }
    }
  }

  test("Simple program") {
    val originalFile = Parser.runParser("""fn main() int export { 42 }""").get()
    val von = ParserVonifier.vonifyFile(originalFile)
    val json = new VonPrinter(JsonSyntax, 120).print(von)
    val loadedFile = ParsedLoader.load(json).get()
    originalFile shouldEqual loadedFile
  }

  test("Strings with special characters") {
    val code = "fn main() str export { \"hello\\u001bworld\" }"
    // FALL NOT TO TEMPTATION
    // Scala has some issues here.
    // The above "\"\\u001b\"" seems like it could be expressed """"\\u001b"""" but it can't.
    // Nothing seems to work:
    // - vassert("\"\\u001b\"" == """"\u001b"""") fails
    // - vassert("\"\\u001b\"" == """"\\u001b"""") fails
    // - vassert("\"\\u001b\"" == """\"\\u001b\"""") fails
    // This took quite a while to figure out.
    // So, just stick with regular scala string literals, scala's good with those.
    // Other tests have this, search TEMPTATION.
    // NOW GO YE AND PROSPER

    // This assert makes sure the above is making the input we actually intend.
    // Real source files from disk are going to have a backslash character and then a u,
    // they won't have the 0x1b byte.
    vassert(code.contains("\\u001b"))
    val originalFile = Parser.runParser(code).get()
    originalFile shouldHave { case ConstantStrPE(_, "hello\u001bworld" ) => }

    val von = ParserVonifier.vonifyFile(originalFile)
    val generatedJsonStr = new VonPrinter(JsonSyntax, 120).print(von)
//    vassert(generatedJsonStr.contains("hello\u001bworld") || generatedJsonStr.contains("hello\u001Bworld"))
//    vassert(!generatedJsonStr.contains("hello\\\\u"))
    val generatedBytes = generatedJsonStr.getBytes(Charset.forName("UTF-8"))

    val loadedJsonStr = new String(generatedBytes, "UTF-8");
    val loadedFile = ParsedLoader.load(loadedJsonStr).get()
    originalFile shouldEqual loadedFile
  }
}
