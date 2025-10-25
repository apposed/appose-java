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
import java.io.IOException;
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
	private static final boolean NORMALIZE = Boolean.parseBoolean(System.getenv().getOrDefault("NORMALIZE", "true"));
	private static final boolean INCLUDE_PRIVATE = Boolean.parseBoolean(System.getenv().getOrDefault("INCLUDE_PRIVATE", "true"));
	private static final boolean INCLUDE_JAVADOC = Boolean.parseBoolean(System.getenv().getOrDefault("INCLUDE_JAVADOC", "false"));

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.err.println("Usage: java DumpApi <source-dir> [source-dir2 ...]");
			System.err.println("Environment variables:");
			System.err.println("  NORMALIZE=true|false      - Convert camelCase to snake_case (default: true)");
			System.err.println("  INCLUDE_PRIVATE=true|false - Include private members (default: true)");
			System.err.println("  INCLUDE_JAVADOC=true|false - Include javadoc comments (default: false)");
			System.exit(1);
		}

		// Collect all Java files from source directories
		List<Path> javaFiles = new ArrayList<>();
		for (String sourceDir : args) {
			Path sourcePath = Paths.get(sourceDir);
			if (Files.isDirectory(sourcePath)) {
				Files.walk(sourcePath)
					.filter(p -> p.toString().endsWith(".java"))
					.sorted()
					.forEach(javaFiles::add);
			}
		}

		// Parse and dump each file
		JavaParser parser = new JavaParser();
		Map<String, TypeDeclaration<?>> allTypes = new TreeMap<>();

		for (Path javaFile : javaFiles) {
			try {
				ParseResult<CompilationUnit> result = parser.parse(javaFile);
				if (result.isSuccessful() && result.getResult().isPresent()) {
					CompilationUnit cu = result.getResult().get();
					for (TypeDeclaration<?> type : cu.getTypes()) {
						String fullName = cu.getPackageDeclaration()
							.map(pd -> pd.getNameAsString() + ".")
							.orElse("") + type.getNameAsString();
						allTypes.put(fullName, type);
					}
				}
			} catch (IOException e) {
				System.err.println("# Warning: Could not parse " + javaFile + ": " + e.getMessage());
			}
		}

		// Dump all types in sorted order
		for (TypeDeclaration<?> type : allTypes.values()) {
			dumpType(type);
		}
	}

	static void dumpType(TypeDeclaration<?> type) {
		// Skip non-public types unless INCLUDE_PRIVATE
		if (!INCLUDE_PRIVATE && !type.isPublic()) return;

		System.out.println();

		// Class/interface javadoc
		if (INCLUDE_JAVADOC) {
			type.getJavadoc().ifPresent(javadoc -> {
				String doc = formatJavadoc(javadoc, "");
				if (!doc.isEmpty()) {
					System.out.println(doc);
				}
			});
		}

		// Class declaration
		StringBuilder classDecl = new StringBuilder("class ");
		classDecl.append(type.getNameAsString());

		// Superclass and interfaces
		List<String> bases = new ArrayList<>();
		if (type instanceof ClassOrInterfaceDeclaration) {
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
		System.out.println(classDecl);

		// Process members in source order
		boolean hasMembers = false;
		for (BodyDeclaration<?> member : type.getMembers()) {
			if (member instanceof EnumConstantDeclaration) {
				EnumConstantDeclaration enumConst = (EnumConstantDeclaration) member;
				System.out.println("    " + nonClassName(enumConst) + " = ...");
				hasMembers = true;
			}
			else if (member instanceof FieldDeclaration) {
				FieldDeclaration field = (FieldDeclaration) member;
				if (!INCLUDE_PRIVATE && !field.isPublic()) continue;

				for (VariableDeclarator var : field.getVariables()) {
					if (INCLUDE_JAVADOC) {
						field.getJavadoc().ifPresent(javadoc -> {
							String doc = formatJavadoc(javadoc, "    ");
							if (!doc.isEmpty()) {
								System.out.println(doc);
							}
						});
					}
					String fieldName = nonClassName(field, var);
					String fieldType = pythonType(var.getType());
					System.out.println("    " + fieldName + ": " + fieldType);
					hasMembers = true;
				}
			}
			else if (member instanceof ConstructorDeclaration) {
				ConstructorDeclaration ctor = (ConstructorDeclaration) member;
				if (!INCLUDE_PRIVATE && !ctor.isPublic()) continue;

				if (INCLUDE_JAVADOC) {
					ctor.getJavadoc().ifPresent(javadoc -> {
						String doc = formatJavadoc(javadoc, "    ");
						if (!doc.isEmpty()) {
							System.out.println(doc);
						}
					});
				}
				System.out.println("    " + formatConstructor(ctor));
				hasMembers = true;
			}
			else if (member instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration) member;
				if (!INCLUDE_PRIVATE && !method.isPublic()) continue;

				if (INCLUDE_JAVADOC) {
					method.getJavadoc().ifPresent(javadoc -> {
						String doc = formatJavadoc(javadoc, "    ");
						if (!doc.isEmpty()) {
							System.out.println(doc);
						}
					});
				}
				System.out.println("    " + formatMethod(method));
				hasMembers = true;
			}
		}

		// Add ... for empty class body
		if (!hasMembers) {
			System.out.println("    ...");
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

	static String formatConstructor(ConstructorDeclaration ctor) {
		StringBuilder sb = new StringBuilder("def __init__(self");

		for (Parameter param : ctor.getParameters()) {
			sb.append(", ");
			String paramName = nonClassName(param);
			String paramType = pythonType(param.getType());
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
			String paramType = pythonType(param.getType());
			sb.append(paramName).append(": ").append(paramType);
		}

		sb.append(") -> ");
		sb.append(pythonType(method.getType()));
		sb.append(": ...");

		return sb.toString();
	}

	static String formatJavadoc(Javadoc javadoc, String indent) {
		StringBuilder sb = new StringBuilder();

		// Main description
		String description = javadoc.getDescription().toText().trim();
		if (!description.isEmpty()) {
			// Format as Python docstring
			sb.append(indent).append("\"\"\"").append(description);

			// Add block tags (@param, @return, etc.)
			List<JavadocBlockTag> blockTags = javadoc.getBlockTags();
			if (!blockTags.isEmpty()) {
				sb.append("\n").append(indent);
				for (JavadocBlockTag tag : blockTags) {
					sb.append("\n").append(indent);
					String tagName = tag.getTagName();
					String tagContent = tag.getContent().toText().trim();

					if (tagName.equals("param")) {
						// Extract parameter name and description
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

		// Handle primitive types
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
		if (typeStr.equals("Object")) return "object";

		// Handle arrays
		if (type.isArrayType()) {
			ArrayType arrayType = type.asArrayType();
			return "list[" + pythonType(arrayType.getComponentType()) + "]";
		}

		// Handle generic types
		if (type.isClassOrInterfaceType()) {
			ClassOrInterfaceType classType = type.asClassOrInterfaceType();
			String baseName = classType.getNameAsString();

			Optional<NodeList<Type>> typeArgs = classType.getTypeArguments();
			if (typeArgs.isPresent()) {
				NodeList<Type> args = typeArgs.get();

				// Map common Java collection types to Python equivalents
				if (baseName.equals("List") || baseName.equals("Collection") ||
				    baseName.equals("Set") || baseName.equals("Queue")) {
					if (args.size() > 0) {
						return "list[" + pythonType(args.get(0)) + "]";
					}
					return "list";
				}
				if (baseName.equals("Map")) {
					if (args.size() == 2) {
						return "dict[" + pythonType(args.get(0)) + ", " + pythonType(args.get(1)) + "]";
					}
					return "dict";
				}
				if (baseName.equals("Optional")) {
					if (args.size() > 0) {
						return pythonType(args.get(0)) + " | None";
					}
					return "object | None";
				}

				// Generic class with type parameters
				StringBuilder result = new StringBuilder(pythonTypeName(baseName));
				result.append("[");
				for (int i = 0; i < args.size(); i++) {
					if (i > 0) result.append(", ");
					result.append(pythonType(args.get(i)));
				}
				result.append("]");
				return result.toString();
			}

			return pythonTypeName(baseName);
		}

		// Type variables (e.g., T, E)
		if (type.isTypeParameter()) {
			return type.asTypeParameter().getNameAsString();
		}

		// Wildcards
		if (type.isWildcardType()) {
			return "object";
		}

		return pythonTypeName(typeStr);
	}

	static String pythonTypeName(String javaName) {
		// Strip package prefixes for simplicity
		String simpleName = javaName;
		if (simpleName.contains(".")) {
			simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
		}
		return simpleName;
	}

	static String toSnakeCase(String camelCase) {
		if (!NORMALIZE) return camelCase;

		// Handle acronyms and consecutive capitals
		String result = camelCase.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");
		result = result.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
		return result.toLowerCase();
	}
}
