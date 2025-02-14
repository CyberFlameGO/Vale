package net.verdagon.vale.driver

import java.io.{BufferedWriter, File, FileNotFoundException, FileOutputStream, FileWriter, OutputStream, PrintStream}
import java.util.InputMismatchException
import net.verdagon.vale.astronomer.{Astronomer, AstronomerErrorHumanizer, ProgramA}
import net.verdagon.vale.hammer.{Hammer, Hamuts, VonHammer}
import net.verdagon.vale.highlighter.{Highlighter, Spanner}
import net.verdagon.vale.metal.{PackageH, ProgramH}
import net.verdagon.vale.parser.{CombinatorParsers, FailedParse, FileP, InputException, ParseErrorHumanizer, ParseFailure, ParseSuccess, ParsedLoader, Parser, ParserVonifier}
import net.verdagon.vale.scout.{Scout, ScoutErrorHumanizer}
import net.verdagon.vale.templar.{Templar, TemplarErrorHumanizer}
import net.verdagon.vale.vivem.Vivem
import net.verdagon.vale.{Builtins, Err, FileCoordinate, FileCoordinateMap, NullProfiler, Ok, PackageCoordinate, Result, vassert, vassertSome, vcheck, vfail, vimpl, vwat}
import net.verdagon.von.{IVonData, JsonSyntax, VonInt, VonPrinter}

import java.nio.charset.Charset
import scala.io.Source
import scala.util.matching.Regex

object Driver {
  val DEFAULT_PACKAGE_COORD = PackageCoordinate("my_module", Vector.empty)

  sealed trait IValestromInput {
    def packageCoord: PackageCoordinate
  }
  case class ModulePathInput(moduleName: String, path: String) extends IValestromInput {
    val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
    override def packageCoord: PackageCoordinate = PackageCoordinate(moduleName, Vector.empty)
  }
  case class DirectFilePathInput(packageCoord: PackageCoordinate, path: String) extends IValestromInput { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
  case class SourceInput(
      packageCoord: PackageCoordinate,
      // Name isnt guaranteed to be unique, we sometimes hand in strings like "builtins.vale"
      name: String,
      code: String) extends IValestromInput { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

  case class Options(
    inputs: Vector[IValestromInput],
//    modulePaths: Map[String, String],
//    packagesToBuild: Vector[PackageCoordinate],
    outputDirPath: Option[String],
    benchmark: Boolean,
    outputVPST: Boolean,
    outputVAST: Boolean,
    outputHighlights: Boolean,
    includeBuiltins: Boolean,
    mode: Option[String], // build v run etc
    verbose: Boolean,
  ) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

  def parseOpts(opts: Options, list: List[String]) : Options = {
    list match {
      case Nil => opts
      case "--output-dir" :: value :: tail => {
        vcheck(opts.outputDirPath.isEmpty, "Multiple output files specified!", InputException)
        parseOpts(opts.copy(outputDirPath = Some(value)), tail)
      }
      case "--output-vpst" :: value :: tail => {
        parseOpts(opts.copy(outputVPST = value.toBoolean), tail)
      }
      case "--output-vast" :: value :: tail => {
        parseOpts(opts.copy(outputVAST = value.toBoolean), tail)
      }
      case "--include-builtins" :: value :: tail => {
        parseOpts(opts.copy(includeBuiltins = value.toBoolean), tail)
      }
      case "--benchmark" :: tail => {
        parseOpts(opts.copy(benchmark = true), tail)
      }
      case "--output-highlights" :: value :: tail => {
        parseOpts(opts.copy(outputHighlights = value.toBoolean), tail)
      }
      case ("-v" | "--verbose") :: tail => {
        parseOpts(opts.copy(verbose = true), tail)
      }
      //          case "--min-size" :: value :: tail =>
      //            parseOpts(opts ++ Map('minsize -> value.toInt), tail)
      //          case string :: opt2 :: tail if isSwitch(opt2) =>
      //            parseOpts(opts ++ Map('infile -> string), list.tail)
      case value :: _ if value.startsWith("-") => throw InputException("Unknown option " + value)
      case value :: tail => {
        if (opts.mode.isEmpty) {
          parseOpts(opts.copy(mode = Some(value)), tail)
        } else {
          if (value.contains("=")) {
            val packageCoordAndPath = value.split("=")
            vcheck(packageCoordAndPath.size == 2, "Arguments can only have 1 equals. Saw: " + value, InputException)
            vcheck(packageCoordAndPath(0) != "", "Must have a module name before a colon. Saw: " + value, InputException)
            vcheck(packageCoordAndPath(1) != "", "Must have a file path after a colon. Saw: " + value, InputException)
            val Array(packageCoordStr, path) = packageCoordAndPath

            val packageCoordinate =
              if (packageCoordStr.contains(".")) {
                val packageCoordinateParts = packageCoordStr.split("\\.")
                PackageCoordinate(packageCoordinateParts.head, packageCoordinateParts.tail.toVector)
              } else {
                PackageCoordinate(packageCoordStr, Vector.empty)
              }
            val input =
              if (path.endsWith(".vale") || path.endsWith(".vpst")) {
                DirectFilePathInput(packageCoordinate, path)
              } else {
                if (packageCoordinate.packages.nonEmpty) {
                  throw InputException("Cannot define a directory for a specific package, only for a module.")
                }
                ModulePathInput(packageCoordinate.module, path)
              }
            parseOpts(opts.copy(inputs = opts.inputs :+ input), tail)
          } else {
            throw InputException("Unrecognized input: " + value)
//            if (value.endsWith(".vale") || value.endsWith(".vpst")) {
//              throw InputException(".vale and .vpst inputs must be prefixed with their module name and a colon.")
//            }
//            val parts =
//              if (value.contains(".")) {
//                value.split("\\.").toVector
//              } else {
//                Vector(value)
//              }
//            val packageCoord = PackageCoordinate(parts.head, parts.tail)
//            parseOpts(opts.copy(packagesToBuild = opts.packagesToBuild :+ packageCoord), tail)
          }
        }
      }
    }
  }

  def resolvePackageContents(
      inputs: Vector[IValestromInput],
      packageCoord: PackageCoordinate):
  Option[Map[String, String]] = {
    val PackageCoordinate(module, packages) = packageCoord

//    println("resolving " + packageCoord + " with inputs:\n" + inputs)

    val sourceInputs =
      inputs.zipWithIndex.filter(_._1.packageCoord.module == module).flatMap({
        case (SourceInput(_, name, code), index) if (packages == Vector.empty) => {
          // All .vpst and .vale direct inputs are considered part of the root paackage.
          Vector((index + "(" + name + ")" -> code))
        }
        case (mpi @ ModulePathInput(_, modulePath), _) => {
//          println("checking with modulepathinput " + mpi)
          val directoryPath = modulePath + packages.map(File.separator + _).mkString("")
//          println("looking in dir " + directoryPath)
          val directory = new java.io.File(directoryPath)
          val filesInDirectory = directory.listFiles()
          if (filesInDirectory == null) {
            Vector()
          } else {
            val inputFiles =
              filesInDirectory.filter(_.getName.endsWith(".vale")) ++
                filesInDirectory.filter(_.getName.endsWith(".vpst"))
            //          println("found files: " + inputFiles)
            val inputFilePaths = inputFiles.map(_.getPath)
            inputFilePaths.toVector.map(filepath => {
              val bufferedSource = Source.fromFile(filepath)
              val code = bufferedSource.getLines.mkString("\n")
              bufferedSource.close
              (filepath -> code)
            })
          }
        }
        case (DirectFilePathInput(_, path), _) => {
          val file = path
          val bufferedSource = Source.fromFile(file)
          val code = bufferedSource.getLines.mkString("\n")
          bufferedSource.close
          Vector((path -> code))
        }
      })
    val filepathToSource = sourceInputs.groupBy(_._1).mapValues(_.head._2)
    vassert(sourceInputs.size == filepathToSource.size, "Input filepaths overlap!")
    Some(filepathToSource)
  }

//  def loadAndParseInputs(
//    startTime: Long,
//    benchmark: Boolean,
//    compilation: Compilation):
//  Result[
//    (FileCoordinateMap[String],
//      FileCoordinateMap[(String, Vector[(Int, Int)])],
//      FileCoordinateMap[FileP],
//      Long),
//    String] = {
//
//    val expandedInputs =
//      inputs.flatMap({
//        case si @ SourceInput(_, _, _) => {
//          Vector(si)
//        }
//        case pi @ PathInput(moduleName, path) => {
//          if (path.endsWith(".vale")) {
//            Vector(pi)
//          } else if (path.endsWith(".vpst")) {
//            Vector(pi)
//          } else {
//            try {
//              val directory = new java.io.File(path)
//              val filesInDirectory = directory.listFiles
//              val inputFiles =
//                filesInDirectory.filter(_.getName.endsWith(".vale")) ++
//                  filesInDirectory.filter(_.getName.endsWith(".vpst"))
//              inputFiles.map(_.getPath).map(x => PathInput(moduleName, x)).toVector
//            } catch {
//              case _ : FileNotFoundException => {
//                throw InputException("Couldn't find file or folder: " + path)
//              }
//            }
//          }
//        }
//      })

    //    val moduleToExpandedInputs =
    //      moduleAndExpandedInputPairs.groupBy(_.moduleName)
//
//    val loadedInputs =
//      expandedInputs.map({
//        case si@SourceInput(_, _, _) => si
//        case PathInput(moduleName, path) => {
//          val contents =
//            (try {
//              val file = new java.io.File(path)
//              val lineSource = Source.fromFile(file)
//              val source = lineSource.getLines().mkString("\n")
//              lineSource.close()
//              source
//            } catch {
//              case _: FileNotFoundException => {
//                throw InputException("Couldn't find file or folder: " + path)
//              }
//            })
//          SourceInput(moduleName, path, contents)
//        }
//        case other => vwat(other.toString)
//      })
//
//    val moduleToPackageToFilepathToCode =
//      loadedInputs.groupBy(_.moduleName).mapValues(loadedInputsInModule => {
//        val paackage = Vector[String]()
//        val filepathToCode =
//          loadedInputsInModule.groupBy(_.path).map({
//            case (path, Vector.empty) => vfail("No files with path: " + path)
//            case (path, Vector(onlyCodeWithThisFilename)) => (path -> onlyCodeWithThisFilename.code)
//            case (path, multipleCodeWithThisFilename) => vfail("Multiple files with path " + path + ": " + multipleCodeWithThisFilename.mkString(", "))
//          })
//        val packageToFilepathToCode = Map(paackage -> filepathToCode)
//        packageToFilepathToCode
//      })
//    val valeCodeMap = FileCoordinateMap(moduleToPackageToFilepathToCode)

//    val startParsingTime = java.lang.System.currentTimeMillis()
//    if (benchmark) {
//      println("Load duration: " + (startParsingTime - startTime))
//    }
//
//    val vpstCodeMap =
//      valeCodeMap.map({ case (fileCoord @ FileCoordinate(_, _, filepath), contents) =>
//        //        println("Parsing " + filepath + "...")
//        if (filepath.endsWith(".vale")) {
//          Parser.runParserForProgramAndCommentRanges(contents) match {
//            case ParseFailure(error) => return Err(ParseErrorHumanizer.humanize(valeCodeMap, fileCoord, error))
//            case ParseSuccess((program0, commentRanges)) => {
//              val von = ParserVonifier.vonifyFile(program0)
//              val json = new VonPrinter(JsonSyntax, 120).print(von)
//              (json, commentRanges)
//            }
//          }
//        } else if (filepath.endsWith(".vpst")) {
//          (contents, Vector.empty)
//        } else {
//          throw new InputException("Unknown input type: " + filepath)
//        }
//      })
//
//    val startLoadingVpstTime = java.lang.System.currentTimeMillis()
//    if (benchmark) {
//      println("Parse .vale duration: " + (startLoadingVpstTime - startParsingTime))
//    }
//
//    val parsedsMap =
//      vpstCodeMap.map({ case (fileCoord, (vpstJson, commentRanges)) =>
//        ParsedLoader.load(vpstJson) match {
//          case ParseFailure(error) => return Err(ParseErrorHumanizer.humanize(valeCodeMap, fileCoord, error))
//          case ParseSuccess(program0) => program0
//        }
//      })
//
//    val doneParsingVpstTime = java.lang.System.currentTimeMillis()
//    if (benchmark) {
//      println("Parse .vpst duration: " + (doneParsingVpstTime - startLoadingVpstTime))
//    }
//
//    Ok((valeCodeMap, vpstCodeMap, parsedsMap, doneParsingVpstTime))
//  }

  def build(opts: Options):
  Result[Option[ProgramH], String] = {
    new java.io.File(opts.outputDirPath.get).mkdirs()
    new java.io.File(opts.outputDirPath.get + "/vast").mkdir()
    new java.io.File(opts.outputDirPath.get + "/vpst").mkdir()

    val startTime = java.lang.System.currentTimeMillis()

    val compilation =
      new FullCompilation(
        Vector(PackageCoordinate.BUILTIN) ++ opts.inputs.map(_.packageCoord).distinct,
        Builtins.getCodeMap().or(packageCoord => resolvePackageContents(opts.inputs, packageCoord)),
        FullCompilationOptions(
          if (opts.verbose) {
            (x => {
              println("#: " + x)
            })
          } else {
            x => Unit // do nothing with it
          },
          opts.verbose,
          new NullProfiler(),
          false
        )
      )

    val startLoadAndParseTime = java.lang.System.currentTimeMillis()

    val parseds =
      compilation.getParseds() match {
        case Err(FailedParse(codeMapSoFar, fileCoord, error)) => return Err(ParseErrorHumanizer.humanize(codeMapSoFar, fileCoord, error))
        case Ok(p) => p
      }
    val valeCodeMap = compilation.getCodeMap().getOrDie()

    if (opts.outputVPST) {
      parseds.map({ case (FileCoordinate(_, _, filepath), (programP, commentRanges)) =>
        val von = ParserVonifier.vonifyFile(programP)
        val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
        val parts = filepath.split("[/\\\\]")
        val vpstFilepath = opts.outputDirPath.get + "/vpst/" + parts.last.replaceAll("\\.vale", ".vpst")
        writeFile(vpstFilepath, vpstJson)
      })
    }

    val startScoutTime = java.lang.System.currentTimeMillis()
    if (opts.benchmark) {
      println("Loading and parsing duration: " + (startScoutTime - startLoadAndParseTime))
    }

    if (opts.outputVAST) {
      compilation.getScoutput() match {
        case Err(e) => return Err(ScoutErrorHumanizer.humanize(valeCodeMap, e))
        case Ok(p) => p
      }

      val startAstronomerTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Scout phase duration: " + (startAstronomerTime - startScoutTime))
      }

      compilation.getAstrouts() match {
        case Err(error) => return Err(AstronomerErrorHumanizer.humanize(valeCodeMap, error))
        case Ok(result) => result
      }

      val startTemplarTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Astronomer phase duration: " + (startTemplarTime - startAstronomerTime))
      }

      compilation.getTemputs() match {
        case Err(error) => return Err(TemplarErrorHumanizer.humanize(opts.verbose, valeCodeMap, error))
        case Ok(x) => x
      }

      val startHammerTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Templar phase duration: " + (startHammerTime - startTemplarTime))
      }

      val programH = compilation.getHamuts()

      val finishTime = java.lang.System.currentTimeMillis()
      if (opts.benchmark) {
        println("Hammer phase duration: " + (finishTime - startHammerTime))
      }

      programH.packages.flatMap({ case (packageCoord, paackage) =>
        val outputVastFilepath =
          opts.outputDirPath.get + "/vast/" +
          (if (packageCoord.isInternal) {
            "__vale"
          } else {
            packageCoord.module + packageCoord.packages.map("." + _).mkString("")
          }) +
          ".vast"
        val json = jsonifyPackage(packageCoord, paackage)
        writeFile(outputVastFilepath, json)
//        println("Wrote VAST to file " + outputVastFilepath)
      })

      Ok(Some(programH))
    } else {
      Ok(None)
    }
  }

  def jsonifyPackage(packageCoord: PackageCoordinate, packageH: PackageH): String = {
    val programV = VonHammer.vonifyPackage(packageCoord, packageH)
    val json = new VonPrinter(JsonSyntax, 120).print(programV)
    json
  }

  def jsonifyProgram(programH: ProgramH): String = {
    val programV = VonHammer.vonifyProgram(programH)
    val json = new VonPrinter(JsonSyntax, 120).print(programV)
    json
  }

  def buildAndOutput(opts: Options) = {
      build(opts) match {
        case Ok(_) => {
        }
        case Err(error) => {
          System.err.println("Error: " + error)
          System.exit(22)
          vfail()
        }
      }
  }

  def run(program: ProgramH, verbose: Boolean): IVonData = {
    if (verbose) {
      Vivem.executeWithPrimitiveArgs(
        program, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
    } else {
      Vivem.executeWithPrimitiveArgs(
        program,
        Vector(),
        new PrintStream(new OutputStream() {
          override def write(b: Int): Unit = {
            // System.out.write(b)
          }
        }),
        () => {
          scala.io.StdIn.readLine()
        },
        (str: String) => {
          print(str)
        })
    }
  }

  def main(args: Array[String]): Unit = {
    try {
      val opts = parseOpts(Options(Vector.empty, None, false, true, true, false, true, None, false), args.toList)
      vcheck(opts.mode.nonEmpty, "No mode!", InputException)
      vcheck(opts.inputs.nonEmpty, "No input files!", InputException)

      opts.mode.get match {
        case "highlight" => {
          vcheck(opts.inputs.size == 1, "Must have exactly 1 input file for highlighting", InputException)
          val Vector(inputFilePath) = opts.inputs

          val compilation =
            new FullCompilation(
              opts.inputs.map(_.packageCoord).distinct,
              Builtins.getCodeMap().or(packageCoord => resolvePackageContents(opts.inputs, packageCoord)),
              FullCompilationOptions(
                if (opts.verbose) {
                  (x => {
                    println("##: " + x)
                  })
                } else {
                  x => Unit // do nothing with it
                },
                opts.verbose,
                new NullProfiler(),
                false))

          val parseds =
            compilation.getParseds() match {
              case Err(FailedParse(codeMapSoFar, fileCoord, error)) => {
                throw InputException(ParseErrorHumanizer.humanize(codeMapSoFar, fileCoord, error))
              }
              case Ok(p) => p
            }
          val valeCodeMap = compilation.getCodeMap().getOrDie()
          val vpstCodeMap = compilation.getVpstMap().getOrDie()

          val code =
            valeCodeMap.moduleToPackagesToFilenameToContents.values.flatMap(_.values.flatMap(_.values)).toVector match {
              case Vector() => throw InputException("No vale code given to highlight!")
              case Vector(x) => x
              case _ => throw InputException("No vale code given to highlight!")
            }
          val Vector(vpst) = vpstCodeMap.moduleToPackagesToFilenameToContents.values.flatMap(_.values.flatMap(_.values)).toVector

          parseds.map({ case (FileCoordinate(module, packages, filepath), (parsed, commentRanges)) =>
            val span = Spanner.forProgram(parsed)
            val highlights = Highlighter.toHTML(code, span, commentRanges)
            if (opts.outputDirPath == Some("")) {
              println(highlights)
            } else {
              val outputFilepath = filepath.replaceAll("\\.vale", ".html")
              writeFile(outputFilepath, highlights)
            }
          })
        }
        case "build" => {
          vcheck(opts.outputDirPath.nonEmpty, "Must specify --output-dir!", InputException)
          buildAndOutput(opts)
        }
        case "run" => {
          throw InputException("Run command has been disabled.");

//          vcheck(args.size >= 2, "Need name!", InputException)
//
//          val optsWithForcedCompile =
//            opts.outputVastFilepath match {
//              case None => opts.copy(outputVastFilepath = Some(""))
//              case Some(_) => opts
//            }
//
//          val program =
//            build(optsWithForcedCompile) match {
//              case Ok(Some(programH)) => programH
//              case Err(error) => {
//                System.err.println(error)
//                System.exit(22)
//                vfail()
//              }
//            }
//
//          val verbose = args.slice(2, args.length).contains("--verbose")
//          val result =
//            if (verbose) {
//              Vivem.executeWithPrimitiveArgs(
//                program, Vector(), System.out, Vivem.emptyStdin, Vivem.nullStdout)
//            } else {
//              Vivem.executeWithPrimitiveArgs(
//                program,
//                Vector(),
//                new PrintStream(new OutputStream() {
//                  override def write(b: Int): Unit = {
//                    // System.out.write(b)
//                  }
//                }),
//                () => {
//                  scala.io.StdIn.readLine()
//                },
//                (str: String) => {
//                  print(str)
//                })
//            }
//          println("Program result: " + result)
//          println()
        }
      }
    } catch {
      case ie @ InputException(msg) => {
        println(msg)
        System.exit(22)
      }
    }
  }

  def writeFile(filepath: String, s: String): Unit = {
    if (filepath == "stdout:") {
      println(s)
    } else {
      val bytes = s.getBytes(Charset.forName("UTF-8"))
      val outputStream = new FileOutputStream(filepath)
      outputStream.write(bytes)
      outputStream.close()
    }
  }
}
