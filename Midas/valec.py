import unittest
import subprocess
from collections import OrderedDict
import os.path
import os
import json
import sys
import shutil
import glob
from os.path import abspath
import argparse
import platform
import os.path

from pathlib import *
from os import path
from subprocess import PIPE
from typing import Dict, Any, List, Callable, Optional


def procrun(args: List[str], **kwargs) -> subprocess.CompletedProcess:
    print("Running: " + " ".join(args))
    return subprocess.run(args, stdout=PIPE, stderr=PIPE, text=True, **kwargs)


class ValeCompiler:
    def valestrom(self,
                  command: str,
                  namespaces_to_build: List[str],
                  user_valestrom_inputs: List[Path],
                  valestrom_options: List[str]) -> subprocess.CompletedProcess:

        valestrom_inputs = user_valestrom_inputs

        if self.build_dir != Path("."):
            if os.path.exists(self.build_dir):
                shutil.rmtree(self.build_dir)
            os.makedirs(self.build_dir)

        valestrom_options.append("--output-dir")
        valestrom_options.append(str(self.build_dir))

        if self.parseds_output_dir != None:
            valestrom_options.append("-op")
            valestrom_options.append(str(self.parseds_output_dir))

        return procrun(
            [
                "java",
                "-cp",
                str(self.valestrom_path / "Valestrom.jar"),
                "net.verdagon.vale.driver.Driver",
                command
            ] + valestrom_options + list((x[0] + "=" + str(x[1])) for x in valestrom_inputs)
        )

    def midas(self,
              vast_file: Path,
              o_files_dir: str,
              midas_options: List[str]) -> subprocess.CompletedProcess:
        return procrun(
            [str(self.midas_path), "--verify", "--output-dir", o_files_dir, str(vast_file)] + midas_options)

    def clang(self,
              o_files: List[Path],
              o_files_dir: Path,
              exe_file: Path,
              census: bool,
              include_path: Optional[Path]) -> subprocess.CompletedProcess:
        if self.windows:
            args = ["cl.exe", '/ENTRY:"main"', '/SUBSYSTEM:CONSOLE', "/Fe:" + str(exe_file)]
            if census:
                args = args + ["/fsanitize=address", "clang_rt.asan_dynamic-x86_64.lib", "clang_rt.asan_dynamic_runtime_thunk-x86_64.lib", "-Wall", "-Werror"]
            args = args + list(str(x) for x in o_files)
            if include_path is not None:
                args.append("-I" + str(include_path))
            return procrun(args)
        else:
            clang = "clang-11" if shutil.which("clang-11") is not None else "clang"
            args = [clang, "-O3", "-lm", "-o", str(exe_file), "-Wall", "-Werror"]
            if census:
                args = args + ["-fsanitize=address", "-fsanitize=leak", "-fno-omit-frame-pointer", "-g"]
            args = args + list(str(x) for x in o_files)
            if include_path is not None:
                args.append("-I" + str(include_path))
            return procrun(args)

    def compile_and_execute(
        self, args: str) -> subprocess.CompletedProcess:


        cwd = Path(os.path.dirname(os.path.realpath(__file__)))

        if len(os.environ.get('VALESTROM_PATH', '')) > 0:
            self.valestrom_path = Path(os.environ.get('VALESTROM_PATH', ''))
        elif path.exists(cwd / "Valestrom.jar"):
            self.valestrom_path = cwd
        elif path.exists(cwd / "test/Valestrom.jar"):
            self.valestrom_path = cwd / "test"
        elif path.exists(cwd / "../Valestrom/Valestrom.jar"):
            self.valestrom_path = cwd / "../Valestrom"
        elif path.exists(cwd / "../Valestrom/out/artifacts/Valestrom_jar/Valestrom.jar"):
            self.valestrom_path = cwd / "../Valestrom/out/artifacts/Valestrom_jar"
        else:
            self.valestrom_path = cwd

        if len(os.environ.get('VALESTD_PATH', '')) > 0:
            self.builtins_path = Path(os.environ.get('VALESTD_PATH', ''))
        elif path.exists(cwd / "src/builtins"):
            self.builtins_path = cwd / "src/builtins"
        elif path.exists(cwd / "builtins"):
            self.builtins_path = cwd / "builtins"
        else:
            self.builtins_path = cwd

        # Maybe we can add a command line param here too, relying on environments is always irksome.
        self.midas_path: Path = cwd
        if len(os.environ.get('VALEC_PATH', '')) > 0:
            print(f"Using midas at {self.midas_path}. ", file=sys.stderr)
            self.midas_path = Path(os.environ.get('VALEC_PATH', ''))
        elif shutil.which("midas") != None:
            self.midas_path = Path(shutil.which("midas"))
        elif path.exists(cwd / "midas"):
            self.midas_path = cwd / "midas"
        elif path.exists(cwd / "midas.exe"):
            self.midas_path = cwd / "midas.exe"
        elif path.exists(cwd / "cmake-build-debug/midas"):
            self.midas_path = cwd / "cmake-build-debug/midas"
        elif path.exists(cwd / "build/midas"):
            self.midas_path = cwd / "build/midas"
        elif path.exists(cwd / "build/midas.exe"):
            self.midas_path = cwd / "build/midas.exe"
        elif path.exists(cwd / "build/Debug/midas.exe"):
            self.midas_path = cwd / "build/Debug/midas.exe"
        elif path.exists(cwd / "build/Release/midas.exe"):
            self.midas_path = cwd / "build/Release/midas.exe"
        elif path.exists(cwd / "x64/Debug/midas.exe"):
            self.midas_path = cwd / "x64/Debug/midas.exe"
        elif path.exists(cwd / "x64/Release/midas.exe"):
            self.midas_path = cwd / "x64/Release/midas.exe"
        else:
            print("No VALEC_PATH in env, and couldn't find one nearby, aborting!", file=sys.stderr)
            sys.exit(1)

        self.windows = platform.system() == 'Windows'

        self.vs_path: str = ''
        if self.windows:
            self.vs_path = os.environ.get('VCInstallDir', '')
            if len(self.vs_path) == 0:
                print('No VCInstallDir in env! To fix:', file=sys.stderr)
                print('1. Make sure Visual Studio is installed.', file=sys.stderr)
                print('2. Run vcvars64.bat. Example location: C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\VC\\Auxiliary\\Build\\vcvars64.bat', file=sys.stderr)
                print('3. Run `echo %%VCInstallDir%%` to verify', file=sys.stderr)
                sys.exit(1)
            print(f"Using Visual Studio at {self.vs_path}. ", file=sys.stderr)
        else:
            pass



        # parser = argparse.ArgumentParser(description='Compiles a Vale program.')
        # parser.add_argument('integers', metavar='N', type=int, nargs='+',
        #                     help='an integer for the accumulator')
        # parser.add_argument('--sum', dest='accumulate', action='store_const',
        #                     const=sum, default=max,
        #                     help='sum the integers (default: find the max)')
        # parser.add_argument('--sum', dest='accumulate', action='store_const',
        #                     const=sum, default=max,
        #                     help='sum the integers (default: find the max)')
        # args = parser.parse_args()

        self.build_dir = None
        exe_file = ("main.exe" if self.windows else "a.out")
        self.parseds_output_dir = None


        print_help = False
        print_version = False
        census = False
        valestrom_options = []
        midas_options = []
        if "--flares" in args:
            args.remove("--flares")
            midas_options.append("--flares")
        if "--benchmark" in args:
            args.remove("--benchmark")
            valestrom_options.append("--benchmark")
        if "--gen-heap" in args:
            args.remove("--gen-heap")
            midas_options.append("--gen-heap")
        if "--census" in args:
            census = True
            args.remove("--census")
            midas_options.append("--census")
        if "--print-mem-overhead" in args:
            args.remove("--print-mem-overhead")
            midas_options.append("--print-mem-overhead")
        if "--verify" in args:
            args.remove("--verify")
            midas_options.append("--verify")
        if "--verbose" in args:
            args.remove("--verbose")
            valestrom_options.append("--verbose")
        if "--include-builtins" in args:
            ind = args.index("--include-builtins")
            del args[ind]
            val = args[ind]
            del args[ind]
            valestrom_options.append("--include-builtins")
            valestrom_options.append(val)
        # if "--output-vpst" in args:
        #     args.remove("--output-vpst")
        #     valestrom_options.append("--output-vpst")
        if "--llvmir" in args:
            args.remove("--llvmir")
            midas_options.append("--llvmir")
        if "--elide-checks-for-known-live" in args:
            args.remove("--elide-checks-for-known-live")
            midas_options.append("--elide-checks-for-known-live")
        if "--override-known-live-true" in args:
            args.remove("--override-known-live-true")
            midas_options.append("--override-known-live-true")
        if "--region-override" in args:
            ind = args.index("--region-override")
            del args[ind]
            val = args[ind]
            del args[ind]
            midas_options.append("--region-override")
            midas_options.append(val)
        if "--cpu" in args:
            ind = args.index("--cpu")
            del args[ind]
            val = args[ind]
            del args[ind]
            midas_options.append("--cpu")
            midas_options.append(val)
        if "--output-dir" in args:
            ind = args.index("--output-dir")
            del args[ind]
            val = args[ind]
            del args[ind]
            self.build_dir = Path(val)
            midas_options.append("--output-dir")
            midas_options.append(val)
        if "--exports-dir" in args:
            ind = args.index("--exports-dir")
            del args[ind]
            val = args[ind]
            del args[ind]
            print("--exports-dir is deprecated, combined with --output-dir.")
        if "--output-vast" in args:
            ind = args.index("--output-vast")
            del args[ind]
            val = args[ind]
            del args[ind]
            valestrom_options.append("--output-vast")
            valestrom_options.append(val)
        if "--output-vpst" in args:
            ind = args.index("--output-vpst")
            del args[ind]
            val = args[ind]
            del args[ind]
            valestrom_options.append("--output-vpst")
            valestrom_options.append(val)
        if "-o" in args:
            ind = args.index("-o")
            del args[ind]
            val = args[ind]
            del args[ind]
            exe_file = val
        if "-op" in args:
            ind = args.index("-op")
            del args[ind]
            val = args[ind]
            del args[ind]
            parseds_output_dir = val
        if "--help" in args:
            ind = args.index("--help")
            del args[ind]
            print_help = True
        if "-v" in args:
            ind = args.index("-v")
            del args[ind]
            print_version = True
        if "--version" in args:
            ind = args.index("--version")
            del args[ind]
            print_version = True

        if self.build_dir is None:
            print("Must specify an output dir with --output-dir.")
            sys.exit(1)

        if len(args) == 0:
            print("Must supply a command, such as 'help', 'build`, 'run', 'version'.")
            sys.exit(22)

        if print_version or args[0] == "version":
            with open(str(self.valestrom_path / "midas-version.txt"), 'r') as f:
                print(f.read())
        elif print_help or args[0] == "help":
            if len(args) < 2:
                with open(str(self.valestrom_path / "valec-version.txt"), 'r') as f:
                    print(f.read())
                with open(str(self.valestrom_path / "valec-help.txt"), 'r') as f:
                    print(f.read())
            elif args[1] == "build":
                with open(str(self.valestrom_path / "valec-help-build.txt"), 'r') as f:
                    print(f.read())
            elif args[1] == "run":
                with open(str(self.valestrom_path / "valec-help-run.txt"), 'r') as f:
                    print(f.read())
            elif args[1] == "paths":
                print("Valestrom path: " + str(self.valestrom_path))
                print("Builtins path: " + str(self.builtins_path))
                print("midas path: " + str(self.midas_path))
            else:
                print("Unknown subcommand: " + args[1])
            sys.exit(0)
        elif args[0] == "build":
            args.pop(0)

            namespaces_to_build = []
            user_valestrom_inputs = []
            user_vast_files = []
            user_c_files = []

            for arg in args:
                if "=" in arg:
                    parts = arg.split("=")
                    if len(parts) != 2:
                        print("Unrecognized input: " + arg)
                        sys.exit(22)
                    project_name = parts[0]
                    contents_path = Path(parts[1]).expanduser()
                    if str(contents_path).endswith(".vale"):
                        user_valestrom_inputs.append([project_name, contents_path])
                    elif str(contents_path).endswith(".vpst"):
                        user_valestrom_inputs.append([project_name, contents_path])
                    elif str(contents_path).endswith(".c"):
                        user_c_files.append(contents_path)
                    elif str(contents_path).endswith(".a"):
                        user_c_files.append(contents_path)
                    elif contents_path.is_dir():
                        # for vale_file in contents_path.rglob('*.vale'):
                        #     with open(str(vale_file), 'r') as f:
                        #         contents = f.read()
                        #         if ("export" in contents) or ("extern" in contents):
                        #             print("Contains export: " + str(vale_file))
                            # user_vale_files.append(Path(vale_file))
                        user_valestrom_inputs.append([project_name, contents_path])
                    else:
                        print("Unrecognized input: " + arg + " (should be project name, then a colon, then a directory or file ending in .vale, .vpst, .vast, .c)")
                        sys.exit(22)
                elif str(arg).endswith(".vast"):
                    user_vast_files.append(Path(arg))
                else:
                    namespaces_to_build.append(arg)

            # for user_valestrom_input in user_valestrom_inputs:
            #     print("Valestrom input: " + user_valestrom_input[0] + "=" + str(user_valestrom_input[1]))
            # for user_vast_file in user_vast_files:
            #     print("VAST input: " + str(user_vast_file))
            # for namespace_to_build in namespaces_to_build:
            #     print("Namespace to build: " + namespace_to_build)

            vast_file = None
            if len(namespaces_to_build) > 0 and len(user_vast_files) == 0:
                proc = self.valestrom("build", namespaces_to_build, user_valestrom_inputs, valestrom_options)

                if proc.returncode == 0:
                    vast_file = self.build_dir / "build.vast"
                    pass
                elif proc.returncode == 22:
                    print(proc.stdout + "\n" + proc.stderr)
                    sys.exit(22)
                else:
                    print(f"Internal error while compiling {user_valestrom_inputs}:\n" + proc.stdout + "\n" + proc.stderr)
                    sys.exit(proc.returncode)
            elif len(user_vast_files) > 0 and len(namespaces_to_build) == 0:
                if len(user_vast_files) > 1:
                    print("Can't have more than one VAST file!")
                    sys.exit(1)
                vast_file = user_vast_files[0]
            elif len(user_vast_files) == 0 and len(namespaces_to_build) == 0:
                print(f"No inputs found!")
                print("")
                if len(user_valestrom_inputs) > 0:
                    print("You've declared where some projects are (via the projectname:path/to/project),")
                    print("but you'll still need to specify one to start building. Example:")
                    print("  python3 midas.py markvale markvale:~/markvale stdlib:~/stdlib")
                    print("Note how even though we already specify where markvale is, we still need to")
                    print("say `markvale` as a lone argument, to start building from there.")
                    print("")
                sys.exit(1)
            else:
                print(f"Both a .vast and non-vast files were specified! If a .vast is specified, it must be the only input.")
                sys.exit(1)


            with open(str(vast_file), 'r') as vast:
                json_root = json.loads(vast.read())
                if "packages" not in json_root:
                    print("Couldn't find packages in .vast!")
                    sys.exit(1)

                package_coords_with_externs = []

                packages = json_root["packages"]
                for package_entry in packages:
                    package = package_entry["package"]
                    externed_name_to_function = package["externNameToFunction"]

                    # for project_name_to_externed_name_to_extern_entry in project_name_to_externed_name_to_extern:
                    #     project_name = project_name_to_externed_name_to_extern_entry["projectName"]
                    #     if project_name == "":
                    #         # We have lots of externs for adding, subtracting, etc. They all use the "" project.
                    #         continue
                    #externed_name_to_extern = project_name_to_externed_name_to_extern_entry["externedNameToExtern"]
                    for externed_name_to_function_entry in externed_name_to_function:
                        # externed_name = externed_name_to_extern_entry["externedName"]
                        externName = externed_name_to_function_entry["externName"]
                        prototype = externed_name_to_function_entry["prototype"]
                        prototype_package_coord = prototype["name"]["packageCoordinate"]

                        project_name = prototype_package_coord["project"]
                        package_steps = prototype_package_coord["packageSteps"]
                        # full_name = externed_name_to_extern_entry["fullName"]

                        if project_name == "":
                            # We have lots of externs for adding, subtracting, etc. They all use the "" project.
                            continue

                        package_coords_with_externs.append([project_name, package_steps])

                directories_with_c = []
                for package_coord in package_coords_with_externs:
                    directory_for_project = None
                    for user_valestrom_input in user_valestrom_inputs:
                        if user_valestrom_input[0] == package_coord[0]:
                            directory_for_project = user_valestrom_input[1]
                    if directory_for_project == None:
                        print("Couldn't find directory for project: " + package_coord[0])
                        sys.exit(1)

                    native_directory = directory_for_project
                    for package_step in package_coord[1]:
                        native_directory = native_directory / package_step
                    native_directory = native_directory / "native"

                    if native_directory.exists() and native_directory not in directories_with_c:
                        directories_with_c.append(native_directory)
                        print("Adding dir with native: " + str(native_directory))

            proc = self.midas(str(vast_file), str(self.build_dir), midas_options)
            # print(proc.stdout)
            # print(proc.stderr)
            if proc.returncode != 0:
                print(f"midas couldn't compile {vast_file}:\n" + proc.stdout + "\n" + proc.stderr, file=sys.stderr)
                sys.exit(1)

            for directory_with_c in directories_with_c:
                for c_file in directory_with_c.rglob('*.c'):
                    user_c_files.append(Path(c_file))

            c_files = user_c_files.copy() + glob.glob(str(self.builtins_path / "*.c")) + glob.glob(str(self.build_dir) + "/*.c") + glob.glob(str(self.build_dir) + "/*/*.c")

            # Get .o or .obj
            o_files = glob.glob(str(vast_file.with_suffix(".o"))) + glob.glob(str(vast_file.with_suffix(".obj")))
            if len(o_files) == 0:
                print("Internal error, no produced object files!")
                sys.exit(1)
            if len(o_files) > 1:
                print("Internal error, multiple produced object files! " + ", ".join(o_files))
                sys.exit(1)


            clang_inputs = o_files + c_files
            proc = self.clang(
                [str(n) for n in clang_inputs],
                self.build_dir,
                self.build_dir / exe_file,
                census,
                self.build_dir)
            # print(proc.stdout)
            # print(proc.stderr)
            if proc.returncode != 0:
                print(f"Linker couldn't compile {clang_inputs}:\n" + proc.stdout + "\n" + proc.stderr, file=sys.stderr)
                sys.exit(1)

            print("Compiled to " + str(self.build_dir / exe_file))
        else:
            print("Unknown subcommand, specify `build`, `run`, etc. Use `help` for more.")
            sys.exit(1)

if __name__ == '__main__':
    ValeCompiler().compile_and_execute(sys.argv[1:])
