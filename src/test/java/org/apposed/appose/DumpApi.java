/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2025 Appose developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.apposed.appose;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithPublicModifier;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dumps Java API in a Python stub-like format for comparison with the other
 * Appose implementations. Includes optional normalization of Java conventions
 * to Python conventions.
 *
 * @author Claude Code
 */
public class DumpApi {
	// Configuration - matches Python stub format.
	private static final boolean NORMALIZE = true;  // Convert camelCase to snake_case
	private static final boolean INCLUDE_PRIVATE = true;  // Include private members (prefixed with _)
	private static final boolean INCLUDE_JAVADOC = false;  // Don't include javadoc (not in stubgen output)
	private static final boolean EXCLUDE_TESTS = false;  // Include test classes for completeness
	private static final boolean GROUP_BY_PACKAGE = true;  // Group by package like Python modules

	// Mapping from Java package/class to Python module file.
	// Based on notes/python-package-structure.md.
	private static final Map<String, String> PACKAGE_TO_MODULE = new HashMap<>();
	static {
		// Core API classes.
		PACKAGE_TO_MODULE.put("org.apposed.appose.Appose", "appose/__init__.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.Environment", "appose/environment.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.Service", "appose/service.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.Service.Task", "appose/service.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.Service.TaskStatus", "appose/service.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.Service.RequestType", "appose/service.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.Service.ResponseType", "appose/service.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.TaskEvent", "appose/service.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.NDArray", "appose/shm.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.NDArray.DType", "appose/shm.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.NDArray.Shape", "appose/shm.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.NDArray.Order", "appose/shm.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.SharedMemory", "appose/shm.api");

		// Subsystem packages - all classes in package go to same file.
		PACKAGE_TO_MODULE.put("org.apposed.appose.scheme", "appose/scheme.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.scheme.Schemes", "appose/scheme.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.shm.Shms", "appose/shm.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.syntax", "appose/syntax.api");

		// Builder subsystem - core in builder/__init__.api, implementations in separate files.
		PACKAGE_TO_MODULE.put("org.apposed.appose.Builder", "appose/builder/__init__.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.BuilderFactory", "appose/builder/__init__.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.Builders", "appose/builder/__init__.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.BaseBuilder", "appose/builder/__init__.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.DynamicBuilder", "appose/builder/__init__.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.SimpleBuilder", "appose/builder/__init__.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.MambaBuilder", "appose/builder/mamba.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.MambaBuilderFactory", "appose/builder/mamba.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.PixiBuilder", "appose/builder/pixi.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.PixiBuilderFactory", "appose/builder/pixi.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.UvBuilder", "appose/builder/uv.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.UvBuilderFactory", "appose/builder/uv.api");

		// Tool subsystem - core in tool/__init__.api, implementations in separate files.
		PACKAGE_TO_MODULE.put("org.apposed.appose.tool.Tool", "appose/tool/__init__.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.tool.Pixi", "appose/tool/pixi.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.tool.Mamba", "appose/tool/mamba.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.tool.Uv", "appose/tool/uv.api");

		// Utility packages - singular naming.
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.Downloads", "appose/util/download.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.Environments", "appose/util/environment.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.FilePaths", "appose/util/filepath.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.Platforms", "appose/util/platform.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.Processes", "appose/util/process.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.Proxies", "appose/util/proxy.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.Types", "appose/util/types.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.Versions", "appose/util/versions.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.XML", "appose/util/xml.api");

		// Workers.
		PACKAGE_TO_MODULE.put("org.apposed.appose.GroovyWorker", "appose/groovy_worker.api");

		// Test classes - map to tests/*.api files (aligned with Python structure).
		PACKAGE_TO_MODULE.put("org.apposed.appose.TestBase", "tests/test_base.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.ServiceTest", "tests/test_service.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.SharedMemoryTest", "tests/test_shm.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.SyntaxTest", "tests/test_syntax.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.TaskExceptionTest", "tests/test_task_exception.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.MambaBuilderTest", "tests/builder/test_mamba.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.PixiBuilderTest", "tests/builder/test_pixi.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.SimpleBuilderTest", "tests/builder/test_simple.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.UvBuilderTest", "tests/builder/test_uv.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.builder.WrapTest", "tests/builder/test_wrap.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.FilePathsTest", "tests/util/test_filepath.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.TypesTest", "tests/util/test_types.api");
		PACKAGE_TO_MODULE.put("org.apposed.appose.util.VersionsTest", "tests/util/test_versions.api");
	}

	// Static utility classes to dump as module-level functions (not as classes).
	private static final Set<String> STATIC_UTILITY_CLASSES = new HashSet<>(Arrays.asList(
		"org.apposed.appose.Appose",
		"org.apposed.appose.builder.Builders",
		"org.apposed.appose.scheme.Schemes",
		"org.apposed.appose.shm.Shms",
		"org.apposed.appose.util.Downloads",
		"org.apposed.appose.util.Environments",
		"org.apposed.appose.util.FilePaths",
		"org.apposed.appose.util.Platforms",
		"org.apposed.appose.util.Processes",
		"org.apposed.appose.util.Proxies",
		"org.apposed.appose.util.Types",
		"org.apposed.appose.util.Versions",
		"org.apposed.appose.util.XML",
		// Test classes - dump as module-level test functions.
		"org.apposed.appose.TestBase",
		"org.apposed.appose.ServiceTest",
		"org.apposed.appose.SharedMemoryTest",
		"org.apposed.appose.SyntaxTest",
		"org.apposed.appose.TaskExceptionTest",
		"org.apposed.appose.builder.MambaBuilderTest",
		"org.apposed.appose.builder.PixiBuilderTest",
		"org.apposed.appose.builder.SimpleBuilderTest",
		"org.apposed.appose.builder.UvBuilderTest",
		"org.apposed.appose.builder.WrapTest",
		"org.apposed.appose.util.FilePathsTest",
		"org.apposed.appose.util.TypesTest",
		"org.apposed.appose.util.VersionsTest"
	));

	// Classes to exclude from API dump (internal implementation details).
	private static final Set<String> EXCLUDED_CLASSES = new HashSet<>(Arrays.asList(
		// Platform-specific SHM implementations (internal details).
		"org.apposed.appose.shm.ShmLinux",
		"org.apposed.appose.shm.ShmMacOS",
		"org.apposed.appose.shm.ShmWindows",
		"org.apposed.appose.shm.ShmBase",
		"org.apposed.appose.shm.CLibrary",
		"org.apposed.appose.shm.Kernel32",
		"org.apposed.appose.shm.LibC",
		// Utility classes (keeping discovery/factory classes, excluding internal helpers).
		"org.apposed.appose.util.Plugins"
	));

	private static PrintWriter currentWriter = null;

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: java DumpApi <output-dir> <source-dir> [source-dir2 ...]");
			System.err.println();
			System.err.println("Dumps Java API in Python stub format for comparison with appose-python.");
			System.err.println("Output will be written to <output-dir>/*.api files.");
			System.exit(1);
		}

		String outputDirArg = args[0];
		Path outputDir = Paths.get(outputDirArg);

		// Create output directory if needed.
		Files.createDirectories(outputDir);

		// Collect all Java files from source directories.
		List<Path> javaFiles = new ArrayList<>();
		for (int i = 1; i < args.length; i++) {
			Path sourcePath = Paths.get(args[i]);
			if (Files.isDirectory(sourcePath)) {
				Files.walk(sourcePath)
					.filter(p -> p.toString().endsWith(".java"))
					.sorted()
					.forEach(javaFiles::add);
			}
		}

		// Parse all files.
		JavaParser parser = new JavaParser();
		Map<String, TypeDeclaration<?>> allTypes = new TreeMap<>();

		for (Path javaFile : javaFiles) {
			try {
				ParseResult<CompilationUnit> result = parser.parse(javaFile);
				if (result.isSuccessful() && result.getResult().isPresent()) {
					CompilationUnit cu = result.getResult().get();
					String packagePrefix = cu.getPackageDeclaration()
						.map(pd -> pd.getNameAsString() + ".")
						.orElse("");

					for (TypeDeclaration<?> type : cu.getTypes()) {
						if (EXCLUDE_TESTS && isTestClass(type)) continue;

						String fullName = packagePrefix + type.getNameAsString();

						// Skip excluded classes.
						if (EXCLUDED_CLASSES.contains(fullName)) continue;

						allTypes.put(fullName, type);

						// Also collect inner classes.
						collectInnerTypes(type, packagePrefix + type.getNameAsString(), allTypes);
					}
				}
			} catch (IOException e) {
				System.err.println("# Warning: Could not parse " + javaFile + ": " + e.getMessage());
			}
		}

		// Group types by target Python module.
		Map<String, List<Map.Entry<String, TypeDeclaration<?>>>> moduleGroups = new LinkedHashMap<>();
		for (Map.Entry<String, TypeDeclaration<?>> entry : allTypes.entrySet()) {
			String fullName = entry.getKey();
			String moduleName = getModuleName(fullName);

			if (moduleName != null) {
				moduleGroups.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(entry);
			}
		}

		// Write each module file.
		for (Map.Entry<String, List<Map.Entry<String, TypeDeclaration<?>>>> moduleEntry : moduleGroups.entrySet()) {
			String moduleName = moduleEntry.getKey();
			List<Map.Entry<String, TypeDeclaration<?>>> types = moduleEntry.getValue();

			Path outputFile = outputDir.resolve(moduleName);

			// Create parent directories if needed.
			Files.createDirectories(outputFile.getParent());

			try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile.toFile()))) {
				currentWriter = writer;

				for (Map.Entry<String, TypeDeclaration<?>> typeEntry : types) {
					dumpType(typeEntry.getValue(), typeEntry.getKey());
				}

				currentWriter = null;
			}

			System.err.println("Wrote " + types.size() + " type(s) to " + moduleName);
		}
	}

	/**
	 * Extracts simple class name for inner classes.
	 * E.g., "org.apposed.appose.Service.Task" -> "Task"
	 *      "org.apposed.appose.Appose" -> "Appose"
	 */
	static String getSimpleClassName(String javaFullName) {
		// Get the last component (class name, possibly with parent).
		int lastDot = javaFullName.lastIndexOf('.');
		if (lastDot == -1) return javaFullName;

		String lastPart = javaFullName.substring(lastDot + 1);

		// Check if this looks like an inner class (has an uppercase letter before it).
		// E.g., "Service.Task" - we want just "Task"
		int innerDot = lastPart.lastIndexOf('.');
		if (innerDot != -1) {
			return lastPart.substring(innerDot + 1);
		}

		return lastPart;
	}

	/**
	 * Determines which Python module file a Java class should be written to.
	 * Returns null if the class should not be included in the API dump.
	 */
	static String getModuleName(String javaFullName) {
		// Check for exact match first (for specific classes).
		if (PACKAGE_TO_MODULE.containsKey(javaFullName)) {
			return PACKAGE_TO_MODULE.get(javaFullName);
		}

		// Check for package-level mappings (e.g., all builder.* classes).
		for (Map.Entry<String, String> entry : PACKAGE_TO_MODULE.entrySet()) {
			String mappedPackage = entry.getKey();
			String moduleName = entry.getValue();

			// Check if this is a package mapping (no uppercase letters = package).
			if (!mappedPackage.matches(".*\\.[A-Z].*") && javaFullName.startsWith(mappedPackage + ".")) {
				return moduleName;
			}
		}

		// No mapping found - skip this class.
		return null;
	}

	static void collectInnerTypes(TypeDeclaration<?> parent, String parentFullName, Map<String, TypeDeclaration<?>> allTypes) {
		for (BodyDeclaration<?> member : parent.getMembers()) {
			if (member instanceof TypeDeclaration) {
				TypeDeclaration<?> innerType = (TypeDeclaration<?>) member;
				if (EXCLUDE_TESTS && isTestClass(innerType)) continue;

				// Use dot notation for inner classes (e.g., Service.Task).
				String fullName = parentFullName + "." + innerType.getNameAsString();

				// Skip excluded classes.
				if (EXCLUDED_CLASSES.contains(fullName)) continue;

				allTypes.put(fullName, innerType);

				// Recursively collect nested inner classes.
				collectInnerTypes(innerType, fullName, allTypes);
			}
		}
	}

	/**
	 * Dumps a static utility class as module-level functions.
	 * In Python, these static methods become global functions in the module.
	 */
	static void dumpStaticUtilityAsModuleFunctions(TypeDeclaration<?> type) {
		PrintWriter out = currentWriter != null ? currentWriter : new PrintWriter(System.out);

		// First output any inner enums (like OperatingSystem, CpuArchitecture).
		for (BodyDeclaration<?> member : type.getMembers()) {
			if (member instanceof EnumDeclaration) {
				EnumDeclaration enumDecl = (EnumDeclaration) member;
				if (enumDecl.isPublic() || INCLUDE_PRIVATE) {
					out.println();
					dumpEnum(enumDecl, out);
				}
			}
		}

		// Check if this is a test class (affects how we handle fields and methods).
		// Test classes have instance fields/methods that should be dumped as module-level constants/functions.
		boolean isTestClass = isTestClass(type);

		// Output fields as module-level constants (static fields for utilities, instance fields for test classes).
		for (BodyDeclaration<?> member : type.getMembers()) {
			if (member instanceof FieldDeclaration) {
				FieldDeclaration field = (FieldDeclaration) member;
				// Include static fields for utilities, or instance/static fields for test classes.
				boolean includeField = (field.isStatic() || isTestClass) && (field.isPublic() || INCLUDE_PRIVATE);
				if (includeField) {
					for (VariableDeclarator var : field.getVariables()) {
						String fieldName = nonClassName(field, var).toUpperCase();
						String fieldType = pythonType(var.getType());
						out.println(fieldName + ": " + fieldType);
					}
				}
			}
		}

		// Collect methods (static methods for utilities, instance methods for test classes).
		Map<String, List<MethodDeclaration>> methodsByName = new LinkedHashMap<>();
		for (BodyDeclaration<?> member : type.getMembers()) {
			if (member instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration) member;
				// Include static methods for utilities, or instance methods for test classes.
				boolean includeMethod = (method.isStatic() || isTestClass) && (method.isPublic() || INCLUDE_PRIVATE);
				if (includeMethod) {
					String methodName = nonClassName(method);
					methodsByName.computeIfAbsent(methodName, k -> new ArrayList<>()).add(method);
				}
			}
		}

		// Output each method as a module-level function (with overloads).
		for (List<MethodDeclaration> methods : methodsByName.values()) {
			for (String methodSig : collapseModuleFunctions(methods)) {
				out.println(methodSig);
			}
		}
	}

	/**
	 * Dumps an enum declaration.
	 */
	static void dumpEnum(EnumDeclaration enumDecl, PrintWriter out) {
		String enumName = enumDecl.getNameAsString();
		out.println("class " + enumName + "(Enum):");

		boolean hasMembers = false;
		for (EnumConstantDeclaration enumConst : enumDecl.getEntries()) {
			String constantName = enumConst.getNameAsString();
			out.println("    " + constantName + " = '" + constantName + "'");
			hasMembers = true;
		}

		if (!hasMembers) {
			out.println("    ...");
		}
	}

	/**
	 * Collapse overloaded module-level functions (similar to collapseMethods but without 'self').
	 */
	static List<String> collapseModuleFunctions(List<MethodDeclaration> methods) {
		if (methods.size() == 1) {
			return Collections.singletonList(formatModuleFunction(methods.get(0)));
		}

		// Try to collapse overloads.
		List<String> result = new ArrayList<>();

		// Sort by parameter count (ascending).
		List<MethodDeclaration> sorted = new ArrayList<>(methods);
		sorted.sort(Comparator.comparingInt(m -> m.getParameters().size()));

		// Check if these are simple overloads that differ only by optional params.
		if (canCollapseToOptionalParams(sorted)) {
			result.add(formatModuleFunctionWithOptionalParams(sorted));
		} else {
			// Output all overloads.
			for (MethodDeclaration method : methods) {
				result.add(formatModuleFunction(method));
			}
		}

		return result;
	}

	/**
	 * Check if a method is annotated with @Nullable.
	 */
	static boolean isNullableMethod(MethodDeclaration method) {
		return method.getAnnotations().stream()
			.anyMatch(a -> a.getNameAsString().equals("Nullable"));
	}

	/**
	 * Check if a parameter is annotated with @Nullable.
	 */
	static boolean isNullableParameter(Parameter param) {
		return param.getAnnotations().stream()
			.anyMatch(a -> a.getNameAsString().equals("Nullable"));
	}

	/**
	 * Format the return type of a method, adding " | None" if the method can return null.
	 */
	static String formatReturnType(MethodDeclaration method) {
		String baseType = pythonType(method.getType());
		if (isNullableMethod(method) && !baseType.equals("None") && !baseType.contains(" | None")) {
			return baseType + " | None";
		}
		return baseType;
	}

	/**
	 * Format a parameter type, adding " | None" if the parameter is nullable.
	 */
	static String formatParameterType(Parameter param) {
		String baseType = pythonType(param.getType());
		if (isNullableParameter(param) && !baseType.equals("None") && !baseType.contains(" | None")) {
			return baseType + " | None";
		}
		return baseType;
	}

	/**
	 * Format a module-level function (no 'self' parameter).
	 */
	static String formatModuleFunction(MethodDeclaration method) {
		StringBuilder sb = new StringBuilder("def ");

		String methodName = nonClassName(method);
		sb.append(methodName).append("(");

		NodeList<Parameter> params = method.getParameters();
		for (int i = 0; i < params.size(); i++) {
			if (i > 0) sb.append(", ");
			Parameter param = params.get(i);
			String paramName = toSnakeCase(param.getNameAsString());
			String paramType = formatParameterType(param);

			// Handle varargs.
			if (param.isVarArgs()) {
				sb.append("*").append(paramName).append(": ").append(paramType);
			} else {
				sb.append(paramName).append(": ").append(paramType);
			}
		}

		sb.append(") -> ");
		sb.append(formatReturnType(method));
		sb.append(": ...");

		return sb.toString();
	}

	/**
	 * Format module-level function with optional parameters based on overloads.
	 */
	static String formatModuleFunctionWithOptionalParams(List<MethodDeclaration> methods) {
		MethodDeclaration longest = methods.get(methods.size() - 1);
		MethodDeclaration shortest = methods.get(0);

		StringBuilder sb = new StringBuilder("def ");

		String methodName = nonClassName(longest);
		sb.append(methodName).append("(");

		NodeList<Parameter> params = longest.getParameters();
		NodeList<Parameter> shortParams = shortest.getParameters();

		// Determine how many parameters are required (non-optional).
		boolean bothHaveVarargs = (shortParams.get(shortParams.size() - 1).isVarArgs() &&
		                           params.get(params.size() - 1).isVarArgs());
		int requiredParamCount = bothHaveVarargs ?
		                         (shortParams.size() - 1) :
		                         shortest.getParameters().size();

		for (int i = 0; i < params.size(); i++) {
			if (i > 0) sb.append(", ");

			Parameter param = params.get(i);
			String paramName = toSnakeCase(param.getNameAsString());
			String paramType = formatParameterType(param);

			// Handle varargs.
			if (param.isVarArgs()) {
				sb.append(paramName).append(": list[").append(paramType).append("]");
				sb.append(" | None = None");
			} else {
				sb.append(paramName).append(": ").append(paramType);

				// Make parameter optional if it's beyond the shortest signature.
				// Only add | None if not already present (from @Nullable annotation).
				if (i >= requiredParamCount) {
					if (!paramType.contains(" | None")) {
						sb.append(" | None");
					}
					sb.append(" = None");
				}
			}
		}

		sb.append(") -> ");
		sb.append(formatReturnType(longest));
		sb.append(": ...");

		return sb.toString();
	}

	static void dumpType(TypeDeclaration<?> type, String fullName) {
		// Skip non-public types unless INCLUDE_PRIVATE
		if (!INCLUDE_PRIVATE && !type.isPublic()) return;

		PrintWriter out = currentWriter != null ? currentWriter : new PrintWriter(System.out);

		// Special case: Static utility classes should be dumped as module-level functions.
		if (STATIC_UTILITY_CLASSES.contains(fullName)) {
			dumpStaticUtilityAsModuleFunctions(type);
			return;
		}

		out.println();

		// Class/interface javadoc.
		if (INCLUDE_JAVADOC) {
			type.getJavadoc().ifPresent(javadoc -> {
				String doc = formatJavadoc(javadoc, "");
				if (!doc.isEmpty()) {
					out.println(doc);
				}
			});
		}

		// Class declaration - use simple name for inner classes.
		StringBuilder classDecl = new StringBuilder("class ");
		String className = getSimpleClassName(fullName);
		classDecl.append(className);

		// Superclass and interfaces.
		List<String> bases = new ArrayList<>();
		if (type instanceof EnumDeclaration) {
			// Enums extend Enum in Python.
			bases.add("Enum");
		}
		else if (type instanceof ClassOrInterfaceDeclaration) {
			ClassOrInterfaceDeclaration classDecl1 = (ClassOrInterfaceDeclaration) type;
			for (ClassOrInterfaceType extended : classDecl1.getExtendedTypes()) {
				String superName = pythonTypeName(extended.getNameAsString());
				if (!superName.equals("Object")) {
					bases.add(superName);
				}
			}
			for (ClassOrInterfaceType implemented : classDecl1.getImplementedTypes()) {
				bases.add(pythonTypeName(implemented.getNameAsString()));
			}
		}
		if (!bases.isEmpty()) {
			classDecl.append("(").append(String.join(", ", bases)).append(")");
		}

		classDecl.append(":");
		out.println(classDecl);

		// Check if this class implements/extends AutoCloseable (for context manager methods).
		boolean isAutoCloseable = false;
		if (type instanceof ClassOrInterfaceDeclaration) {
			ClassOrInterfaceDeclaration classDecl1 = (ClassOrInterfaceDeclaration) type;
			// Check implemented interfaces (for classes).
			for (ClassOrInterfaceType implemented : classDecl1.getImplementedTypes()) {
				if (implemented.getNameAsString().equals("AutoCloseable")) {
					isAutoCloseable = true;
					break;
				}
			}
			// Check extended interfaces (for interfaces).
			if (!isAutoCloseable) {
				for (ClassOrInterfaceType extended : classDecl1.getExtendedTypes()) {
					if (extended.getNameAsString().equals("AutoCloseable")) {
						isAutoCloseable = true;
						break;
					}
				}
			}
		}

		// Collect methods by name for overload detection.
		Map<String, List<MethodDeclaration>> methodsByName = new LinkedHashMap<>();
		List<ConstructorDeclaration> constructors = new ArrayList<>();

		// Process enum constants first (for enum types).
		boolean hasMembers = false;
		if (type instanceof EnumDeclaration) {
			EnumDeclaration enumDecl = (EnumDeclaration) type;
			for (EnumConstantDeclaration enumConst : enumDecl.getEntries()) {
				// Python enums use string values: INITIAL = 'INITIAL'
				String constantName = enumConst.getNameAsString();
				out.println("    " + constantName + " = '" + constantName + "'");
				hasMembers = true;
			}
		}

		// Process other members in source order.
		for (BodyDeclaration<?> member : type.getMembers()) {
			if (member instanceof EnumConstantDeclaration) {
				// Already handled above for EnumDeclaration.
				continue;
			}
			else if (member instanceof FieldDeclaration) {
				FieldDeclaration field = (FieldDeclaration) member;
				if (!INCLUDE_PRIVATE && !field.isPublic()) continue;

				for (VariableDeclarator var : field.getVariables()) {
					if (INCLUDE_JAVADOC) {
						field.getJavadoc().ifPresent(javadoc -> {
							String doc = formatJavadoc(javadoc, "    ");
							if (!doc.isEmpty()) {
								out.println(doc);
							}
						});
					}
					String fieldName = nonClassName(field, var);
					String fieldType = pythonType(var.getType());
					out.println("    " + fieldName + ": " + fieldType);
					hasMembers = true;
				}
			}
			else if (member instanceof ConstructorDeclaration) {
				ConstructorDeclaration ctor = (ConstructorDeclaration) member;
				if (!INCLUDE_PRIVATE && !ctor.isPublic()) continue;
				constructors.add(ctor);
			}
			else if (member instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration) member;
				if (!INCLUDE_PRIVATE && !method.isPublic()) continue;

				String methodName = nonClassName(method);
				methodsByName.computeIfAbsent(methodName, k -> new ArrayList<>()).add(method);
			}
		}

		// Output constructors (collapsed if overloaded).
		if (!constructors.isEmpty()) {
			for (String ctorSig : collapseConstructors(constructors)) {
				out.println("    " + ctorSig);
				hasMembers = true;
			}
		}

		// Output methods (collapsed if overloaded).
		for (List<MethodDeclaration> methods : methodsByName.values()) {
			for (String methodSig : collapseMethods(methods)) {
				out.println("    " + methodSig);
				hasMembers = true;
			}
		}

		// Add context manager methods for AutoCloseable classes.
		if (isAutoCloseable) {
			// Check if __enter__ and __exit__ are already defined.
			boolean hasEnter = false;
			boolean hasExit = false;
			for (BodyDeclaration<?> member : type.getMembers()) {
				if (member instanceof MethodDeclaration) {
					String methodName = ((MethodDeclaration) member).getNameAsString();
					if (methodName.equals("__enter__")) hasEnter = true;
					if (methodName.equals("__exit__")) hasExit = true;
				}
			}

			// Add Python context manager protocol methods.
			if (!hasEnter) {
				out.println("    def __enter__(self) -> " + className + ": ...");
				hasMembers = true;
			}
			if (!hasExit) {
				out.println("    def __exit__(self, exc_type: type[BaseException] | None, exc_value: BaseException | None, exc_tb: types.TracebackType | None) -> None: ...");
				hasMembers = true;
			}
		}

		// Add ... for empty class body.
		if (!hasMembers) {
			out.println("    ...");
		}
	}

	private static String prefix(Object o) {
		return o instanceof NodeWithPublicModifier &&
			!((NodeWithPublicModifier<?>) o).isPublic() ? "_" : "";
	}

	private static String nonClassName(NodeWithSimpleName<?> n) {
		return prefix(n) + toSnakeCase(n.getNameAsString());
	}

	private static String nonClassName(NodeWithPublicModifier<?> p, NodeWithSimpleName<?> n) {
		return prefix(p) + toSnakeCase(n.getNameAsString());
	}

	/**
	 * Collapse overloaded constructors into minimal set of signatures with optional parameters.
	 */
	static List<String> collapseConstructors(List<ConstructorDeclaration> constructors) {
		if (constructors.size() == 1) {
			return Collections.singletonList(formatConstructor(constructors.get(0)));
		}

		// For now, output all overloads - full collapsing is complex.
		// TODO: Implement smart merging of overloads.
		List<String> result = new ArrayList<>();
		for (ConstructorDeclaration ctor : constructors) {
			result.add(formatConstructor(ctor));
		}
		return result;
	}

	/**
	 * Collapse overloaded methods into minimal set of signatures with optional parameters.
	 */
	static List<String> collapseMethods(List<MethodDeclaration> methods) {
		if (methods.size() == 1) {
			return Collections.singletonList(formatMethod(methods.get(0)));
		}

		// Try to collapse overloads.
		List<String> result = new ArrayList<>();

		// Sort by parameter count (ascending) to process simpler signatures first.
		List<MethodDeclaration> sorted = new ArrayList<>(methods);
		sorted.sort(Comparator.comparingInt(m -> m.getParameters().size()));

		// Check if these are simple overloads that differ only by optional params.
		if (canCollapseToOptionalParams(sorted)) {
			result.add(formatMethodWithOptionalParams(sorted));
		} else {
			// Output all overloads.
			for (MethodDeclaration method : methods) {
				result.add(formatMethod(method));
			}
		}

		return result;
	}

	/**
	 * Check if methods can be collapsed by making some parameters optional.
	 */
	static boolean canCollapseToOptionalParams(List<MethodDeclaration> methods) {
		if (methods.size() != 2) return false; // Only handle 2-overload case for now

		MethodDeclaration shortest = methods.get(0);
		MethodDeclaration longest = methods.get(methods.size() - 1);

		// Must have same static/instance nature.
		if (shortest.isStatic() != longest.isStatic()) return false;

		// Must have same return type.
		if (!shortest.getType().equals(longest.getType())) return false;

		NodeList<Parameter> shortParams = shortest.getParameters();
		NodeList<Parameter> longParams = longest.getParameters();

		if (shortParams.size() >= longParams.size()) return false;
		if (shortParams.isEmpty() || longParams.isEmpty()) return false;

		// Special case: both end with varargs of same type.
		// E.g., groovy(String... args) and groovy(List<String> classPath, String... args)
		// Or: java(String main, String... args) and java(String main, List<String> cp, String... args)
		if (shortParams.get(shortParams.size() - 1).isVarArgs() &&
		    longParams.get(longParams.size() - 1).isVarArgs()) {
			Type shortVarType = shortParams.get(shortParams.size() - 1).getType();
			Type longVarType = longParams.get(longParams.size() - 1).getType();
			if (shortVarType.equals(longVarType)) {
				// Check that all non-varargs params in shortest match beginning of longest.
				for (int i = 0; i < shortParams.size() - 1; i++) {
					if (!shortParams.get(i).getType().equals(longParams.get(i).getType())) {
						return false;
					}
				}
				return true; // Can collapse by making middle params optional
			}
		}

		// Standard case: Check if shortest is a prefix of longest (common pattern for optional params).
		for (int i = 0; i < shortParams.size(); i++) {
			if (!shortParams.get(i).getType().equals(longParams.get(i).getType())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Format method with optional parameters based on overloads.
	 */
	static String formatMethodWithOptionalParams(List<MethodDeclaration> methods) {
		MethodDeclaration longest = methods.get(methods.size() - 1);
		MethodDeclaration shortest = methods.get(0);

		StringBuilder sb = new StringBuilder("def ");
		boolean isStatic = longest.isStatic();

		String methodName = nonClassName(longest);
		sb.append(methodName).append("(");

		if (!isStatic) {
			sb.append("self");
		}

		NodeList<Parameter> params = longest.getParameters();
		NodeList<Parameter> shortParams = shortest.getParameters();

		// Determine how many parameters are required (non-optional).
		// If both end with varargs, required count is all non-varargs params from shortest.
		boolean bothHaveVarargs = (shortParams.get(shortParams.size() - 1).isVarArgs() &&
		                           params.get(params.size() - 1).isVarArgs());
		int requiredParamCount = bothHaveVarargs ?
		                         (shortParams.size() - 1) :  // Exclude varargs from count
		                         shortest.getParameters().size();

		for (int i = 0; i < params.size(); i++) {
			if (!isStatic || i > 0) sb.append(", ");

			Parameter param = params.get(i);
			String paramName = nonClassName(param);
			String paramType = pythonType(param.getType());

			// Handle varargs - convert to optional list parameter instead of *args.
			if (param.isVarArgs()) {
				sb.append(paramName).append(": list[").append(paramType).append("]");
				// Varargs are always optional in Python.
				sb.append(" | None = None");
			} else {
				sb.append(paramName).append(": ").append(paramType);

				// Make parameter optional if it's beyond the shortest signature.
				if (i >= requiredParamCount) {
					sb.append(" | None = None");
				}
			}
		}

		sb.append(") -> ");
		sb.append(pythonType(longest.getType()));
		sb.append(": ...");

		return sb.toString();
	}

	static String formatConstructor(ConstructorDeclaration ctor) {
		StringBuilder sb = new StringBuilder("def __init__(self");

		for (Parameter param : ctor.getParameters()) {
			sb.append(", ");
			String paramName = nonClassName(param);
			String paramType = formatParameterType(param);
			sb.append(paramName).append(": ").append(paramType);
		}

		sb.append(") -> None: ...");
		return sb.toString();
	}

	static String formatMethod(MethodDeclaration method) {
		StringBuilder sb = new StringBuilder("def ");

		// Static methods don't have 'self'
		boolean isStatic = method.isStatic();

		String methodName = nonClassName(method);
		sb.append(methodName).append("(");

		if (!isStatic) {
			sb.append("self");
		}

		NodeList<Parameter> params = method.getParameters();
		for (int i = 0; i < params.size(); i++) {
			if (!isStatic || i > 0) sb.append(", ");
			Parameter param = params.get(i);
			String paramName = nonClassName(param);
			String paramType = formatParameterType(param);

			// Handle varargs.
			if (param.isVarArgs()) {
				sb.append("*").append(paramName).append(": ").append(paramType);
			} else {
				sb.append(paramName).append(": ").append(paramType);
			}
		}

		sb.append(") -> ");
		sb.append(formatReturnType(method));
		sb.append(": ...");

		return sb.toString();
	}

	static String formatJavadoc(Javadoc javadoc, String indent) {
		StringBuilder sb = new StringBuilder();

		// Main description.
		String description = javadoc.getDescription().toText().trim();
		if (!description.isEmpty()) {
			// Format as Python docstring.
			sb.append(indent).append("\"\"\"").append(description);

			// Add block tags (@param, @return, etc.).
			List<JavadocBlockTag> blockTags = javadoc.getBlockTags();
			if (!blockTags.isEmpty()) {
				sb.append("\n").append(indent);
				for (JavadocBlockTag tag : blockTags) {
					sb.append("\n").append(indent);
					String tagName = tag.getTagName();
					String tagContent = tag.getContent().toText().trim();

					if (tagName.equals("param")) {
						// Extract parameter name and description.
						String[] parts = tagContent.split("\\s+", 2);
						if (parts.length == 2) {
							String paramName = toSnakeCase(parts[0]);
							sb.append("@param ").append(paramName).append(": ").append(parts[1]);
						}
					} else if (tagName.equals("return")) {
						sb.append("@return: ").append(tagContent);
					} else {
						sb.append("@").append(tagName).append(": ").append(tagContent);
					}
				}
			}

			sb.append("\n").append(indent).append("\"\"\"");
		}

		return sb.toString();
	}

	static String pythonType(Type type) {
		String typeStr = type.asString();

		// Handle primitive types.
		if (typeStr.equals("void")) return "None";
		if (typeStr.equals("boolean") || typeStr.equals("Boolean")) return "bool";
		if (typeStr.equals("byte") || typeStr.equals("Byte")) return "int";
		if (typeStr.equals("short") || typeStr.equals("Short")) return "int";
		if (typeStr.equals("int") || typeStr.equals("Integer")) return "int";
		if (typeStr.equals("long") || typeStr.equals("Long")) return "int";
		if (typeStr.equals("float") || typeStr.equals("Float")) return "float";
		if (typeStr.equals("double") || typeStr.equals("Double")) return "float";
		if (typeStr.equals("char") || typeStr.equals("Character")) return "str";
		if (typeStr.equals("String")) return "str";
		if (typeStr.equals("Object")) return "Any";

		// Handle common Java types (check both simple and fully qualified names).
		if (typeStr.equals("File") || typeStr.endsWith(".File")) return "Path";
		if (typeStr.equals("Path") || typeStr.endsWith(".Path")) return "Path";
		if (typeStr.equals("URL") || typeStr.endsWith(".URL")) return "str";
		if (typeStr.equals("URI") || typeStr.endsWith(".URI")) return "str";
		if (typeStr.equals("Thread") || typeStr.endsWith(".Thread")) return "threading.Thread";
		if (typeStr.equals("Process") || typeStr.endsWith(".Process")) return "subprocess.Popen";
		if (typeStr.equals("ByteBuffer") || typeStr.endsWith(".ByteBuffer")) return "bytes";
		if (typeStr.equals("InputStream") || typeStr.endsWith(".InputStream") ||
		    typeStr.equals("OutputStream") || typeStr.endsWith(".OutputStream") ||
		    typeStr.equals("PrintWriter") || typeStr.endsWith(".PrintWriter") ||
		    typeStr.equals("BufferedReader") || typeStr.endsWith(".BufferedReader")) return "object";

		// Handle arrays.
		if (type.isArrayType()) {
			ArrayType arrayType = type.asArrayType();
			return "list[" + pythonType(arrayType.getComponentType()) + "]";
		}

		// Handle generic types.
		if (type.isClassOrInterfaceType()) {
			ClassOrInterfaceType classType = type.asClassOrInterfaceType();
			String baseName = classType.getNameAsString();

			// Check for simple type mappings first (File, Thread, Process, etc.).
			if (baseName.equals("File")) return "Path";
			if (baseName.equals("Process")) return "subprocess.Popen";
			if (baseName.equals("Thread")) return "threading.Thread";
			if (baseName.equals("ByteBuffer")) return "bytes";
			if (baseName.equals("PrintWriter") || baseName.equals("BufferedReader") ||
			    baseName.equals("InputStream") || baseName.equals("OutputStream")) return "object";
			if (baseName.equals("URL") || baseName.equals("URI")) return "str";
			if (baseName.equals("ProgressConsumer")) return "Callable[[int, int], None]";

			Optional<NodeList<Type>> typeArgs = classType.getTypeArguments();
			if (typeArgs.isPresent()) {
				NodeList<Type> args = typeArgs.get();

				// Map common Java collection types to Python equivalents.
				if (baseName.equals("List") || baseName.equals("ArrayList") ||
				    baseName.equals("LinkedList")) {
					if (args.size() > 0) {
						return "list[" + pythonType(args.get(0)) + "]";
					}
					return "list";
				}
				if (baseName.equals("Set") || baseName.equals("HashSet") ||
				    baseName.equals("TreeSet")) {
					if (args.size() > 0) {
						return "set[" + pythonType(args.get(0)) + "]";
					}
					return "set";
				}
				if (baseName.equals("Collection") || baseName.equals("Iterable") ||
				    baseName.equals("Queue") || baseName.equals("Deque")) {
					if (args.size() > 0) {
						return "list[" + pythonType(args.get(0)) + "]";
					}
					return "list";
				}
				if (baseName.equals("Map") || baseName.equals("HashMap") ||
				    baseName.equals("TreeMap") || baseName.equals("LinkedHashMap") ||
				    baseName.equals("Dictionary")) {
					if (args.size() == 2) {
						return "dict[" + pythonType(args.get(0)) + ", " + pythonType(args.get(1)) + "]";
					}
					return "dict";
				}
				if (baseName.equals("Optional")) {
					if (args.size() > 0) {
						return pythonType(args.get(0)) + " | None";
					}
					return "Any | None";
				}

				// Function types.
				if (baseName.equals("Consumer")) {
					if (args.size() == 1) {
						return "Callable[[" + pythonType(args.get(0)) + "], None]";
					}
				}
				if (baseName.equals("BiConsumer")) {
					if (args.size() == 2) {
						return "Callable[[" + pythonType(args.get(0)) + ", " + pythonType(args.get(1)) + "], None]";
					}
				}
				if (baseName.equals("Supplier")) {
					if (args.size() == 1) {
						return "Callable[[], " + pythonType(args.get(0)) + "]";
					}
				}
				if (baseName.equals("Function")) {
					if (args.size() == 2) {
						return "Callable[[" + pythonType(args.get(0)) + "], " + pythonType(args.get(1)) + "]";
					}
				}
				if (baseName.equals("BiFunction")) {
					if (args.size() == 3) {
						return "Callable[[" + pythonType(args.get(0)) + ", " + pythonType(args.get(1)) + "], " + pythonType(args.get(2)) + "]";
					}
				}
				if (baseName.equals("Predicate")) {
					if (args.size() == 1) {
						return "Callable[[" + pythonType(args.get(0)) + "], bool]";
					}
				}
				if (baseName.equals("Class")) {
					if (args.size() == 1) {
						Type arg = args.get(0);
						// For wildcards (Class<?>), just use 'type'
						if (arg.isWildcardType()) {
							return "type";
						}
						return "type[" + pythonType(arg) + "]";
					}
					return "type";
				}

				// Generic class with type parameters.
				StringBuilder result = new StringBuilder(pythonTypeName(baseName));
				result.append("[");
				for (int i = 0; i < args.size(); i++) {
					if (i > 0) result.append(", ");
					result.append(pythonType(args.get(i)));
				}
				result.append("]");
				return result.toString();
			}

			// Special handling for non-generic types.
			if (baseName.equals("Runnable")) return "Callable[[], None]";
			if (baseName.equals("Consumer") || baseName.equals("Supplier") ||
			    baseName.equals("Predicate") || baseName.equals("Function")) {
				return "Callable";
			}

			return pythonTypeName(baseName);
		}

		// Type variables (e.g., T, E).
		if (type.isTypeParameter()) {
			return type.asTypeParameter().getNameAsString();
		}

		// Wildcards.
		if (type.isWildcardType()) {
			return "Any";
		}

		return pythonTypeName(typeStr);
	}

	static String pythonTypeName(String javaName) {
		// Strip package prefixes for simplicity.
		String simpleName = javaName;
		if (simpleName.contains(".")) {
			simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
		}
		return simpleName;
	}

	static String toSnakeCase(String camelCase) {
		if (!NORMALIZE) return camelCase;

		// Handle acronyms and consecutive capitals.
		String result = camelCase.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");
		result = result.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
		return result.toLowerCase();
	}

	static boolean isTestClass(TypeDeclaration<?> type) {
		String name = type.getNameAsString();
		// Check if it's a test class by name or inheritance.
		if (name.endsWith("Test") || name.endsWith("Tests") || name.startsWith("Test")) {
			return true;
		}
		// Check for test annotations.
		if (type.getAnnotations().stream().anyMatch(a ->
			a.getNameAsString().contains("Test") ||
			a.getNameAsString().equals("RunWith"))) {
			return true;
		}
		// Check if it extends a test base class.
		if (type instanceof ClassOrInterfaceDeclaration) {
			ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
			for (ClassOrInterfaceType extended : classDecl.getExtendedTypes()) {
				String superName = extended.getNameAsString();
				if (superName.contains("Test")) {
					return true;
				}
			}
		}
		return false;
	}

}
