/**
 * Copyright (c) 2016, Jurgen J. Vinju, Centrum Wiskunde & Informatica (CWI) All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.library.lang.json.io;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Map.Entry;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IDateTime;
import io.usethesource.vallang.IExternalValue;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.IRational;
import io.usethesource.vallang.IReal;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.visitors.IValueVisitor;

import com.google.gson.stream.JsonWriter;

import org.rascalmpl.util.DateTimeConversions;

/**
 * This class streams am IValue stream directly to an JSon stream. Useful to communicate IValues to browsers.
 */
public class JsonValueWriter {
  private DateTimeFormatter format;
  private boolean constructorsAsObjects = true;
  private boolean nodesAsObjects = true;
  private boolean datesAsInts = true;
  private boolean unpackedLocations = false;
  
  public JsonValueWriter() {
    setCalendarFormat("yyyy-MM-dd'T'HH:mm:ssZ");
  }
  
  /**
   * Builder method to set the format to use for all date-time values encoded as strings
   */
  public JsonValueWriter setCalendarFormat(String format) {
    this.format = DateTimeFormatter.ofPattern(format);
    return this;
  }
  
  public JsonValueWriter setConstructorsAsObjects(boolean setting) {
    this.constructorsAsObjects = setting;
    return this;
  }
  
  public JsonValueWriter setNodesAsObjects(boolean setting) {
    this.nodesAsObjects = setting;
    return this;
  }
  
  public JsonValueWriter setDatesAsInt(boolean setting) {
    this.datesAsInts = setting;
    return this;
  }
  
  public JsonValueWriter setUnpackedLocations(boolean setting) {
      this.unpackedLocations = setting;
      return this;
    }

  public void write(JsonWriter out, IValue value) throws IOException {
    value.accept(new IValueVisitor<Void, IOException>() {

      @Override
      public Void visitString(IString o) throws IOException {
        out.value(o.getValue());
        return null;
      }

      @Override
      public Void visitReal(IReal o) throws IOException {
        // TODO: warning might loose precision here?!
        out.value(o.doubleValue());
        return null;
      }

      @Override
      public Void visitRational(IRational o) throws IOException {
          out.value(o.getStringRepresentation());
          return null;
      }

      @Override
      public Void visitList(IList o) throws IOException {
        out.beginArray();
        for (IValue v : o) {
          v.accept(this);
        }
        out.endArray();
        return null;
      }

      @Override
      public Void visitSet(ISet o) throws IOException {
        out.beginArray();
        for (IValue v : o) {
          v.accept(this);
        }
        out.endArray();
        return null;
      }

      @Override
      public Void visitSourceLocation(ISourceLocation o) throws IOException {
          if (unpackedLocations) {
              out.beginObject();
              
              out.name("scheme");
              out.value(o.getScheme());
              
              out.name("authority");
              out.value(o.getAuthority());
              
              out.name("path");
              out.value(o.getPath());
              
              if (!o.getFragment().isEmpty()) {
                  out.name("fragment");
                  out.value(o.getFragment());
              }
              
              if (!o.getQuery().isEmpty()) {
                  out.name("query");
                  out.value(o.getQuery());
              }
              
              if (o.hasOffsetLength()) {
                  out.name("offset");
                  out.value(o.getOffset());
                  out.name("length");
                  out.value(o.getLength());
              }
              
              if (o.hasLineColumn()) {
                  out.name("begin");
                  out.beginArray();
                  out.value(o.getBeginLine());
                  out.value(o.getBeginColumn());
                  out.endArray();
                  
                  out.name("end");
                  out.beginArray();
                  out.value(o.getEndLine());
                  out.value(o.getEndColumn());
                  out.endArray();
              }
              
              out.endObject();
          }
          else {
              if (!o.hasOffsetLength()) {
                  if ("file".equals(o.getScheme())) {
                      out.value(o.getPath());
                  }
                  else {
                      out.value(o.getURI().toASCIIString());
                  }
              }
              else {
                  out.value(o.toString());
              }
          }
        
        return null;
      }

      @Override
      public Void visitTuple(ITuple o) throws IOException {
        out.beginArray();
        for (IValue v : o) {
          v.accept(this);
        }
        out.endArray();
        return null;
      }

      @Override
      public Void visitNode(INode o) throws IOException {
        if (nodesAsObjects) {
          out.beginObject();
          out.name(o.getName());
          out.beginObject();
          int i = 0;
          for (IValue arg : o) {
            out.name("arg" + i++); 
            arg.accept(this);
          }
          for (Entry<String,IValue> e : o.asWithKeywordParameters().getParameters().entrySet()) {
            out.name(e.getKey()); 
            e.getValue().accept(this); 
          }
          out.endObject();
          out.endObject();
        }
        else {
          out.beginArray();
          out.value(o.getName());
          out.beginArray();
          for (IValue arg : o) {
            arg.accept(this);
          }
          out.endArray();
          
          if (o.asWithKeywordParameters().hasParameters()) {
            out.beginObject();
            for (Entry<String,IValue> e : o.asWithKeywordParameters().getParameters().entrySet()) {
              out.name(e.getKey()); 
              e.getValue().accept(this); 
            }
            out.endObject();
          }
          
          out.endArray();
        }
        
        return null;
      }

      @Override
      public Void visitConstructor(IConstructor o) throws IOException {
        if (constructorsAsObjects) {
          out.beginObject();
          out.name(o.getName());
          out.beginObject();
          int i = 0;
          for (IValue arg : o) {
            out.name(o.getConstructorType().getFieldName(i));
            arg.accept(this);
            i++;
          }
          for (Entry<String,IValue> e : o.asWithKeywordParameters().getParameters().entrySet()) {
            out.name(e.getKey()); 
            e.getValue().accept(this); 
          }
          out.endObject();
          out.endObject();
        }
        else {
          out.beginArray();
          out.value(o.getName());
          out.beginArray();
          for (IValue arg : o) {
            arg.accept(this);
          }
          out.endArray();
          
          if (o.asWithKeywordParameters().hasParameters()) {
            out.beginObject();
            for (Entry<String,IValue> e : o.asWithKeywordParameters().getParameters().entrySet()) {
              out.name(e.getKey()); 
              e.getValue().accept(this); 
            }
            out.endObject();
          }
          
          out.endArray();
        }
        return null;
      }

      @Override
      public Void visitInteger(IInteger o) throws IOException {
        // TODO: may loose precision
        out.value(o.longValue());
        return null;
      }

      @Override
      public Void visitMap(IMap o) throws IOException {
        if (o.getKeyType().isString()) {
          out.beginObject();
          for (IValue key : o) {
            out.name(((IString) key).getValue()); 
            o.get(key).accept(this); 
          }
          out.endObject();
        }
        else if (o.getKeyType().isSourceLocation() && !unpackedLocations) {
          out.beginObject();
          for (IValue key : o) {
            ISourceLocation l = (ISourceLocation) key;
            
            if (!l.hasOffsetLength()) {
              if ("file".equals(l.getScheme())) { 
                out.name(l.getPath()); 
              }
              else {
                out.name(l.getURI().toASCIIString());
              }
            }
            else {
              out.name(l.toString());
            }

            o.get(key).accept(this); 
          }
          out.endObject();
        }
        else {
          out.beginArray();
          for (IValue key : o) {
            out.beginArray();
            key.accept(this);
            o.get(key).accept(this);
            out.endArray();
          }
          out.endArray();
        }
        
        return null;
      }

      @Override
      public Void visitBoolean(IBool boolValue) throws IOException {
        out.value(boolValue.getValue());
        return null;
      }

      @Override
      public Void visitExternal(IExternalValue externalValue) throws IOException {
        // TODO
        throw new IOException("External values are not supported by JSon serialisation yet");
      }

      @Override
      public Void visitDateTime(IDateTime o) throws IOException {
        if (datesAsInts) {
          out.value(o.getInstant());
          return null;
        }
        else {
          out.value(format.format(DateTimeConversions.dateTimeToJava(o)));
          return null;
        }
      }
    });
    }
}