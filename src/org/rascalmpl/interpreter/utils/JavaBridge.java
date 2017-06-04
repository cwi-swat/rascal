/*******************************************************************************
 * Copyright (c) 2009-2017 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Anya Helene Bagge - anya@ii.uib.no (Univ. Bergen)
 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Mark Hills - Mark.Hills@cwi.nl (CWI)
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
*******************************************************************************/
package org.rascalmpl.interpreter.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.rascalmpl.ast.Expression;
import org.rascalmpl.ast.FunctionDeclaration;
import org.rascalmpl.ast.KeywordFormal;
import org.rascalmpl.ast.Parameters;
import org.rascalmpl.ast.Tag;
import org.rascalmpl.ast.TagString;
import org.rascalmpl.ast.Tags;
import org.rascalmpl.interpreter.Configuration;
import org.rascalmpl.interpreter.IEvaluator;
import org.rascalmpl.interpreter.IEvaluatorContext;
import org.rascalmpl.interpreter.asserts.ImplementationError;
import org.rascalmpl.interpreter.env.Environment;
import org.rascalmpl.interpreter.result.Result;
import org.rascalmpl.interpreter.staticErrors.JavaCompilation;
import org.rascalmpl.interpreter.staticErrors.JavaMethodLink;
import org.rascalmpl.interpreter.staticErrors.MissingTag;
import org.rascalmpl.interpreter.staticErrors.NonAbstractJavaFunction;
import org.rascalmpl.interpreter.staticErrors.UndeclaredJavaMethod;
import org.rascalmpl.interpreter.types.DefaultRascalTypeVisitor;
import org.rascalmpl.interpreter.types.RascalType;
import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IDateTime;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.INumber;
import io.usethesource.vallang.IRational;
import io.usethesource.vallang.IReal;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.type.Type;
import org.rascalmpl.values.uptr.ITree;


public class JavaBridge {
	private static final String JAVA_CLASS_TAG = "javaClass";
	
	private final List<ClassLoader> loaders;
	
	private final static JavaClasses javaClasses = new JavaClasses();
	
	private final IValueFactory vf;
	
	private final Map<Class<?>, Object> instanceCache;
	
	private final Map<Class<?>, JavaFileManager> fileManagerCache;

	private final Configuration config;

	public JavaBridge(List<ClassLoader> classLoaders, IValueFactory valueFactory, Configuration config) {
		this.loaders = classLoaders;
		this.vf = valueFactory;
		this.instanceCache = new HashMap<Class<?>, Object>();
		this.fileManagerCache = new ConcurrentHashMap<Class<?>, JavaFileManager>();
		this.config = config;
		
		if (ToolProvider.getSystemJavaCompiler() == null) {
			throw new ImplementationError("Could not find an installed System Java Compiler, please provide a Java Runtime that includes the Java Development Tools (JDK 1.6 or higher).");
		}
	}

	public <T> Class<T> compileJava(ISourceLocation loc, String className, String source) {
		return compileJava(loc, className, getClass(), source);
	}
	
	public <T> Class<T> compileJava(ISourceLocation loc, String className, Class<?> parent, String source) {
		try {
			// watch out, if you start sharing this compiler, classes will not be able to reload
			List<String> commandline = Arrays.asList(new String[] {"-cp", config.getRascalJavaClassPathProperty()});
			JavaCompiler<T> javaCompiler = new JavaCompiler<>(parent.getClassLoader(), fileManagerCache.get(parent), commandline);
			Class<T> result = javaCompiler.compile(className, source, null, Object.class);
			fileManagerCache.put(result, javaCompiler.getFileManager());
			return result;
		} 
		catch (ClassCastException e) {
			throw new JavaCompilation(e.getMessage(), loc);
		} 
		catch (JavaCompilerException e) {
		    if (!e.getDiagnostics().getDiagnostics().isEmpty()) {
		        Diagnostic<? extends JavaFileObject> msg = e.getDiagnostics().getDiagnostics().iterator().next();
		        throw new JavaCompilation(msg.getMessage(null) + " at " + msg.getLineNumber() + ", " + msg.getColumnNumber() + " with classpath [" + config.getRascalJavaClassPathProperty() + "]", loc);
		    }
		    else {
		        throw new JavaCompilation(e.getMessage(), loc);
		    }
		}
	}

	private String getClassName(FunctionDeclaration declaration) {
		Tags tags = declaration.getTags();
		
		if (tags.hasTags()) {
			for (Tag tag : tags.getTags()) {
				if (Names.name(tag.getName()).equals(JAVA_CLASS_TAG)) {
					if(tag.hasContents()){
						String contents = ((TagString.Lexical) tag.getContents()).getString();

						if (contents.length() > 2 && contents.startsWith("{")) {
							contents = contents.substring(1, contents.length() - 1);
						}
						return contents;
					}
				}
			}
		}
		
		return "";
	}
	

	private Class<?>[] getJavaTypes(Parameters parameters, Environment env, boolean hasReflectiveAccess) {
		List<Expression> formals = parameters.getFormals().getFormals();
		int arity = formals.size();
		int kwArity = 0;
		List<KeywordFormal> keywordFormals = null;
		if(parameters.getKeywordFormals().isDefault()){
			keywordFormals = parameters.getKeywordFormals().getKeywordFormalList();
			kwArity = keywordFormals.size();
		}		
		
		Class<?>[] classes = new Class<?>[arity + kwArity + (hasReflectiveAccess ? 1 : 0)];
		int i = 0;
		while (i < arity) {
			Class<?> clazz;
			
			if (i == arity - 1 && parameters.isVarArgs()) {
				clazz = IList.class;
			}
			else {
				clazz = toJavaClass(formals.get(i), env);
			}
			
			if (clazz != null) {
			  classes[i++] = clazz;
			}
		}
		
		while(i < arity + kwArity){
			Class<?> clazz = toJavaClass(keywordFormals.get(i - arity).getType(), env);
			if (clazz != null) {
				  classes[i++] = clazz;
				}
		}
		
		if (hasReflectiveAccess) {
			classes[arity + kwArity] = IEvaluatorContext.class;
		}
		
		return classes;
	}
	
	private Class<?> toJavaClass(Expression formal, Environment env) {
		return toJavaClass(toValueType(formal, env));
	}
	
	private Class<?> toJavaClass(org.rascalmpl.ast.Type tp, Environment env) {
		return toJavaClass(tp.typeOf(env, true, null));
	}

	private Class<?> toJavaClass(io.usethesource.vallang.type.Type type) {
		return type.accept(javaClasses);
	}
	
	private io.usethesource.vallang.type.Type toValueType(Expression formal, Environment env) {
		return formal.typeOf(env, true, null);
	}
	
	private static class JavaClasses extends DefaultRascalTypeVisitor<Class<?>, RuntimeException> {

		public JavaClasses() {
			super(IValue.class);
		}

		@Override
		public Class<?> visitBool(io.usethesource.vallang.type.Type boolType) {
			return IBool.class;
		}

		@Override
		public Class<?> visitReal(io.usethesource.vallang.type.Type type) {
			return IReal.class;
		}

		@Override
		public Class<?> visitInteger(io.usethesource.vallang.type.Type type) {
			return IInteger.class;
		}
		
		@Override
		public Class<?> visitRational(io.usethesource.vallang.type.Type type) {
			return IRational.class;
		}
		
		@Override
		public Class<?> visitNumber(io.usethesource.vallang.type.Type type) {
			return INumber.class;
		}

		@Override
		public Class<?> visitList(io.usethesource.vallang.type.Type type) {
			return IList.class;
		}

		@Override
		public Class<?> visitMap(io.usethesource.vallang.type.Type type) {
			return IMap.class;
		}

		@Override
		public Class<?> visitAlias(io.usethesource.vallang.type.Type type) {
			return type.getAliased().accept(this);
		}

		@Override
		public Class<?> visitAbstractData(io.usethesource.vallang.type.Type type) {
			return IConstructor.class;
		}

		@Override
		public Class<?> visitSet(io.usethesource.vallang.type.Type type) {
			return ISet.class;
		}

		@Override
		public Class<?> visitSourceLocation(io.usethesource.vallang.type.Type type) {
			return ISourceLocation.class;
		}

		@Override
		public Class<?> visitString(io.usethesource.vallang.type.Type type) {
			return IString.class;
		}

		@Override
		public Class<?> visitNode(io.usethesource.vallang.type.Type type) {
			return INode.class;
		}

		@Override
		public Class<?> visitConstructor(io.usethesource.vallang.type.Type type) {
			return IConstructor.class;
		}

		@Override
		public Class<?> visitTuple(io.usethesource.vallang.type.Type type) {
			return ITuple.class;
		}

		@Override
		public Class<?> visitValue(io.usethesource.vallang.type.Type type) {
			return IValue.class;
		}

		@Override
		public Class<?> visitVoid(io.usethesource.vallang.type.Type type) {
			return null;
		}

		@Override
		public Class<?> visitParameter(io.usethesource.vallang.type.Type parameterType) {
			return parameterType.getBound().accept(this);
		}

		@Override
		public Class<?> visitDateTime(Type type) {
			return IDateTime.class;
		}
		
		@Override
		public Class<?> visitNonTerminal(RascalType type)
				throws RuntimeException {
			return ITree.class;
		}
	}
	
	public synchronized Object getJavaClassInstance(Class<?> clazz){
		Object instance = instanceCache.get(clazz);
		if(instance != null){
			return instance;
		}
		
		try{
			Constructor<?> constructor = clazz.getConstructor(IValueFactory.class);
			instance = constructor.newInstance(vf);
			instanceCache.put(clazz, instance);
			return instance;
		} catch (IllegalArgumentException e) {
			throw new ImplementationError(e.getMessage(), e);
		} catch (InstantiationException e) {
			throw new ImplementationError(e.getMessage(), e);
		} catch (IllegalAccessException e) {
			throw new ImplementationError(e.getMessage(), e);
		} catch (InvocationTargetException e) {
			throw new ImplementationError(e.getMessage(), e);
		} catch (SecurityException e) {
			throw new ImplementationError(e.getMessage(), e);
		} catch (NoSuchMethodException e) {
			throw new ImplementationError(e.getMessage(), e);
		} 
	}
	
	public synchronized Object getJavaClassInstance(FunctionDeclaration func){
		String className = getClassName(func);

		try {
			for(ClassLoader loader : loaders){
				try{
					Class<?> clazz = loader.loadClass(className);

					Object instance = instanceCache.get(clazz);
					if(instance != null){
						return instance;
					}

					Constructor<?> constructor = clazz.getConstructor(IValueFactory.class);
					instance = constructor.newInstance(vf);
					instanceCache.put(clazz, instance);
					return instance;
				}
				catch(ClassNotFoundException e){
					continue;
				} 
			}
		} 
		catch(NoClassDefFoundError e) {
			throw new JavaMethodLink(className, e.getMessage(), func, e);
		}
		catch (IllegalArgumentException e) {
			throw new JavaMethodLink(className, e.getMessage(), func, e);
		} catch (InstantiationException e) {
			throw new JavaMethodLink(className, e.getMessage(), func, e);
		} catch (IllegalAccessException e) {
			throw new JavaMethodLink(className, e.getMessage(), func, e);
		} catch (InvocationTargetException e) {
			throw new JavaMethodLink(className, e.getMessage(), func, e);
		} catch (SecurityException e) {
			throw new JavaMethodLink(className, e.getMessage(), func, e);
		} catch (NoSuchMethodException e) {
			throw new JavaMethodLink(className, e.getMessage(), func, e);
		}
		
		throw new JavaMethodLink(className, "class not found", func, null);
	}

	public Method lookupJavaMethod(IEvaluator<Result<IValue>> eval, FunctionDeclaration func, Environment env, boolean hasReflectiveAccess){
		if(!func.isAbstract()){
			throw new NonAbstractJavaFunction(func);
		}
		
		String className = getClassName(func);
		String name = Names.name(func.getSignature().getName());
		
		if(className.length() == 0){	// TODO: Can this ever be thrown since the Class instance has 
										// already been identified via the javaClass tag.
			throw new MissingTag(JAVA_CLASS_TAG, func);
		}
		
		for(ClassLoader loader : loaders){
			try{
				Class<?> clazz = loader.loadClass(className);
				Parameters parameters = func.getSignature().getParameters();
				Class<?>[] javaTypes = getJavaTypes(parameters, env, hasReflectiveAccess);

				try{
					Method m;
					
					if(javaTypes.length > 0){ // non-void
						m = clazz.getMethod(name, javaTypes);
					}else{
						m = clazz.getMethod(name);
					}

					return m;
				}catch(SecurityException e){
					throw RuntimeExceptionFactory.permissionDenied(vf.string(e.getMessage()), eval.getCurrentAST(), eval.getStackTrace());
				}catch(NoSuchMethodException e){
					throw new UndeclaredJavaMethod(e.getMessage(), func);
				}
			}catch(ClassNotFoundException e){
				continue;
			}
		}
		
		throw new UndeclaredJavaMethod(className + "." + name, func);
	}

	/**
	 * Same as saveToJar("", clazz, outPath, false);
	 */
	public void saveToJar(Class<?> clazz, OutputStream outStream) throws IOException {
		saveToJar("", clazz, null, outStream, false);
	}
	
	/**
	 * Save a compiled class and associated classes to a jar file.
	 *  
	 * With a packageName = "" and recursive = false, it will save clazz and any classes
	 * compiled from the same source (I think); this is probably what you want.
	 * 
	 * @param packageName package name prefix to search for classes, or "" for all 
	 * @param clazz a class that has been previously compiled by this bridge
	 * @param outStream output stream
	 * @param recursive whether to retrieve classes from rest of the the JavaFileManager hierarchy
	 * 	
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void saveToJar(String packageName, Class<?> clazz, OutputStream outStream,
			boolean recursive) throws IOException {
		saveToJar(packageName, clazz, null, outStream, recursive);
	}
	
	/**
	 * Save a compiled class and associated classes to a jar file.
	 *  
	 * With a packageName = "" and recursive = false, it will save clazz and any classes
	 * compiled from the same source (I think); this is probably what you want.
	 * 
	 * @param packageName package name prefix to search for classes, or "" for all 
	 * @param clazz a class that has been previously compiled by this bridge
	 * @param mainClazz a class that will be installed as the "Main-Class" of a runnable jar
	 * @param outStream output stream
	 * @param recursive whether to retrieve classes from rest of the the JavaFileManager hierarchy
	 * 	
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void saveToJar(String packageName, Class<?> clazz, Class<?> mainClazz, OutputStream outStream,
			boolean recursive) throws IOException {
		JavaFileManager manager = fileManagerCache.get(clazz);
		List<JavaFileObject> list = new ArrayList<>();
		
		for(JavaFileObject obj : manager.list(StandardLocation.CLASS_PATH, packageName,
			Collections.singleton(JavaFileObject.Kind.CLASS), false))
			list.add(obj);

		
		if (list.iterator().hasNext()) {
			Manifest manifest = new Manifest();
			manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,
					"1.0");

			if(mainClazz != null) {
				manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClazz.getName());
			}
			manifest.getMainAttributes().put(new Attributes.Name("X-Rascal-Saved-Class"), clazz.getName());
			JarOutputStream target = new JarOutputStream(outStream, manifest);
			JarEntry entry = new JarEntry("META-INF/");
			target.putNextEntry(entry);
			Collection<String> dirs = new ArrayList<>();

			for (JavaFileObject o : list) {
				String binaryName = manager.inferBinaryName(StandardLocation.CLASS_PATH, o);
				String path = binaryName.replace(".", "/");
				
				makeJarDirs(target, dirs, path);
				entry = new JarEntry(path + ".class");
				entry.setTime(o.getLastModified());
				target.putNextEntry(entry);
				
				try(InputStream stream = o.openInputStream()) {
					byte[] buffer = new byte[8192];
					int c = stream.read(buffer);
					while (c > -1) {
						target.write(buffer, 0, c);
						c = stream.read(buffer);
					}
				}
				target.closeEntry();

			}
			
			if(mainClazz != null) {
				String name = mainClazz.getName();
				String path = name.replace(".", "/") + ".class";
				
				if(path.contains("/")) {
					String dir = path.substring(0, path.lastIndexOf('/'));
					StringBuilder dirTmp = new StringBuilder(dir.length());
					for (String d : dir.split("/")) {
						dirTmp.append(d);
						dirTmp.append("/");
						String tmp = dirTmp.toString();
						if (!dirs.contains(tmp)) {
							dirs.add(tmp);
							entry = new JarEntry(tmp);
							target.putNextEntry(entry);
						}
					}
				}
				entry = new JarEntry(path);
				target.putNextEntry(entry);
				
				try(InputStream stream = mainClazz.getClassLoader().getResourceAsStream(path)) {
					byte[] buffer = new byte[8192];
					int c = stream.read(buffer);
					while (c > -1) {
						target.write(buffer, 0, c);
						c = stream.read(buffer);
					}
				}
				target.closeEntry();
			}

			target.close();
		}

	}

	private void makeJarDirs(JarOutputStream target, Collection<String> dirs,
			String path) throws IOException {
		JarEntry entry;
		if(path.contains("/")) {
			String dir = path.substring(0, path.lastIndexOf('/'));
			while(dir.startsWith("/"))
				dir = dir.substring(1);
			StringBuilder dirTmp = new StringBuilder(dir.length());
			for (String d : dir.split("/")) {
				dirTmp.append(d);
				dirTmp.append("/");
				String tmp = dirTmp.toString();
				if (!dirs.contains(tmp)) {
					dirs.add(tmp);
					entry = new JarEntry(tmp);
					target.putNextEntry(entry);
				}
			}
		}
	}
}
