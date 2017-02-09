/*******************************************************************************
 * Copyright (c) 2009-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
*******************************************************************************/
package org.rascalmpl.library.util;

//This code was generated by Rascal API gen
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class Maybe {
	public static final TypeStore typestore = new TypeStore();

	private static final TypeFactory tf = TypeFactory.getInstance();

	
	public static final Type Maybe = tf.abstractDataType(typestore, "Maybe",tf.parameterType("A",tf.valueType()));

	public static final Type Maybe_nothing = tf.constructor(typestore,Maybe,"nothing");
	public static final Type Maybe_just = tf.constructor(typestore,Maybe,"just",tf.parameterType("A",tf.valueType()),"val");
					
	
	 public static IValue Maybe_just_val(IConstructor c){
	return (IValue)c.get(0);
}

	private static final class InstanceHolder {
		public final static Maybe factory = new Maybe();
	}
	  
	public static Maybe getInstance() {
		return InstanceHolder.factory;
	}
	
	
	public static TypeStore getStore() {
		return typestore;
	}
}