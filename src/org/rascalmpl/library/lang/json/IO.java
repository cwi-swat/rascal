/*******************************************************************************
 * Copyright (c) 2009-2013 CWI All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 
 * * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI * Mark Hills - Mark.Hills@cwi.nl (CWI) * Arnold
 * Lankamp - Arnold.Lankamp@cwi.nl * Bert Lisser - Bert.Lisser@cwi.nl
 *******************************************************************************/
package org.rascalmpl.library.lang.json;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;

import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.library.lang.json.io.IValueAdapter;
import org.rascalmpl.library.lang.json.io.JSONReadingTypeVisitor;
import org.rascalmpl.library.lang.json.io.JsonValueReader;
import org.rascalmpl.library.lang.json.io.JsonValueWriter;
import org.rascalmpl.types.TypeReifier;
import org.rascalmpl.uri.URIResolverRegistry;
import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeStore;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class IO {
	private final IValueFactory values;

	public IO(IValueFactory values) {
		super();
		this.values = values;
	}
	
	public IString toJSON(IValue value) {
		  return toJSON(value, this.values.bool(false));
	}


	public IString toJSON(IValue value, IBool compact) {
		// System.err.println("hallo");
		IValueAdapter adap = new IValueAdapter(compact.getValue());
		// System.err.println(adap);
		Gson gson = new GsonBuilder()
		.registerTypeAdapter(IValue.class, adap)
		.enableComplexMapKeySerialization()
		.setDateFormat(DateFormat.LONG)
		.setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
		.setVersion(1.0)
		.disableHtmlEscaping()  // Bert Lisser
		.create();
		try {
			String json = gson.toJson(value, new TypeToken<IValue>() {}.getType());
			return values.string(json);
		} catch (Exception e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
		}
	}
	
	public IValue fromJSON(IValue type, IString src) {
        TypeStore store = new TypeStore();
        Type start = new TypeReifier(values).valueToType((IConstructor) type, store);
        Gson gson = new GsonBuilder()
        .enableComplexMapKeySerialization()
        .setDateFormat(DateFormat.LONG)
        .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
        .setVersion(1.0)
        .create();
        Object obj = gson.fromJson(src.getValue(), Object.class);
        try {
            return JSONReadingTypeVisitor.read(obj, values, store, start);
        }
        catch (IOException e) {
            throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
        }
    }
    
	
	public IValue readJSON(IValue type, ISourceLocation loc, IBool implicitConstructors, IBool implicitNodes, IString dateTimeFormat, IBool lenient) {
      TypeStore store = new TypeStore();
      Type start = new TypeReifier(values).valueToType((IConstructor) type, store);
      
      try (JsonReader in = new JsonReader(URIResolverRegistry.getInstance().getCharacterReader(loc))) {
		in.setLenient(lenient.getValue());
        return new JsonValueReader(values, store)
            .setConstructorsAsObjects(implicitConstructors.getValue())
            .setNodesAsObjects(implicitNodes.getValue())
            .setCalendarFormat(dateTimeFormat.getValue())
            .read(in, start);
      }
      catch (IOException e) {
        throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
      }
      catch (NullPointerException e) {
        e.printStackTrace();
        throw RuntimeExceptionFactory.io(values.string("NPE"), null, null);
      }
    }
	
	public IValue parseJSON(IValue type, IString src, IBool implicitConstructors, IBool implicitNodes, IString dateTimeFormat, IBool lenient) {
	      TypeStore store = new TypeStore();
	      Type start = new TypeReifier(values).valueToType((IConstructor) type, store);
	      
	      try (JsonReader in = new JsonReader(new StringReader(src.getValue()))) {
			in.setLenient(lenient.getValue());
	        return new JsonValueReader(values, store)
	            .setConstructorsAsObjects(implicitConstructors.getValue())
	            .setNodesAsObjects(implicitNodes.getValue())
	            .setCalendarFormat(dateTimeFormat.getValue())
	            .read(in, start);
	      }
	      catch (IOException e) {
	        throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
	      }
	      catch (NullPointerException e) {
	        throw RuntimeExceptionFactory.io(values.string("NPE"), null, null);
	      }
	    }
	
	public void writeJSON(ISourceLocation loc, IValue value, IBool implicitConstructors, IBool implicitNodes, IBool unpackedLocations, IString dateTimeFormat, IBool dateTimeAsInt, IInteger indent) {
	    try (JsonWriter out = new JsonWriter(new OutputStreamWriter(URIResolverRegistry.getInstance().getOutputStream(loc, false), Charset.forName("UTF8")))) {
	        if (indent.intValue() > 0) {
	            out.setIndent("        ".substring(0, indent.intValue() % 9));
	        }
	        
	        new JsonValueWriter()
	        .setConstructorsAsObjects(implicitConstructors.getValue())
	        .setNodesAsObjects(implicitNodes.getValue())
	        .setCalendarFormat(dateTimeFormat.getValue())
	        .setDatesAsInt(dateTimeAsInt.getValue())
	        .setUnpackedLocations(unpackedLocations.getValue())
	        .write(out, value);
	    } catch (IOException e) {
	        throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
	    } 
	}

	public IString asJSON(IValue value, IBool implicitConstructors, IBool implicitNodes, IBool unpackedLocations, IString dateTimeFormat, IBool dateTimeAsInt, IInteger indent) {
	    StringWriter string = new StringWriter();

	    try (JsonWriter out = new JsonWriter(string)) {
	        if (indent.intValue() > 0) {
	            out.setIndent("        ".substring(0, indent.intValue() % 9));
	        }
	        new JsonValueWriter()
	        .setConstructorsAsObjects(implicitConstructors.getValue())
	        .setNodesAsObjects(implicitNodes.getValue())
	        .setCalendarFormat(dateTimeFormat.getValue())
	        .setDatesAsInt(dateTimeAsInt.getValue())
	        .setUnpackedLocations(unpackedLocations.getValue())
	        .write(out, value);

	        return values.string(string.toString());
	    } catch (IOException e) {
	        throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
	    } 
	}
	
	
	
}
