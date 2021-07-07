package org.rascalmpl.library.lang.json.io;

import static org.rascalmpl.library.lang.json.Factory.JSON;
import static org.rascalmpl.library.lang.json.Factory.JSON_array;
import static org.rascalmpl.library.lang.json.Factory.JSON_boolean;
import static org.rascalmpl.library.lang.json.Factory.JSON_ivalue;
import static org.rascalmpl.library.lang.json.Factory.JSON_null;
import static org.rascalmpl.library.lang.json.Factory.JSON_number;
import static org.rascalmpl.library.lang.json.Factory.JSON_object;
import static org.rascalmpl.library.lang.json.Factory.JSON_string;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.rascalmpl.interpreter.asserts.NotYetImplemented;
import org.rascalmpl.types.NonTerminalType;
import org.rascalmpl.values.RascalValueFactory;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IDateTime;
import io.usethesource.vallang.IExternalValue;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.IMapWriter;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.IRational;
import io.usethesource.vallang.IReal;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISetWriter;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.type.ITypeVisitor;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;
import io.usethesource.vallang.visitors.IValueVisitor;

@SuppressWarnings("rawtypes")
public class JSONReadingTypeVisitor implements
		ITypeVisitor<IValue, IOException> {

	private static final TypeFactory tf = TypeFactory.getInstance();
	private final IValueFactory vf;
	private final TypeStore ts;
	private final Stack<Object> stack;

	public static IValue read(Object obj, IValueFactory vf, TypeStore ts, Type t) throws IOException {
		JSONReadingTypeVisitor v = new JSONReadingTypeVisitor(obj, vf, ts);
		return v.read(t);
	}
	
	private JSONReadingTypeVisitor(Object obj, IValueFactory vf, TypeStore ts) {
		this.stack = new Stack<Object>();
		this.stack.push(obj);
		this.vf = vf;
		this.ts = ts;
	}

	private IValue read(Type type) throws IOException {
		while (type.isAliased()) {
			type = type.getAliased();
		}

		if (type == tf.valueType()) {
			return visitValue(type);
		}

		if (type == tf.numberType()) {
			return visitNumber(type);
		}
		
		if (type == JSON) {
			return readPlainJSON();
		}

		// NB: why not treat isNode same as isNumber:
		// because nodes have values, but nums do not.

		stack.push(contents()); // skip tag
		IValue v = type.accept(this);
		stack.pop();
		return v;
	}


	private String tag() {
		assert stack.peek() instanceof List && ((List)stack.peek()).size() == 2;
		List m = (List)stack.peek();
		return (String) m.get(0);
	}
	
	private Object contents() {
		assert stack.peek() instanceof List && ((List)stack.peek()).size() == 2;
		List m = (List)stack.peek();
		return m.get(1);
	}
	
	@Override
	public IValue visitReal(Type type) throws IOException {
		return vf.real(((Double)stack.peek()).doubleValue());
	}

	@Override
	public IValue visitInteger(Type type) throws IOException {
		return vf.integer(((Double)stack.peek()).longValue());
	}

	@Override
	public IValue visitRational(Type type) throws IOException {
		// [ num, denom ]
		List l = (List)stack.peek();
		long num = ((Double)l.get(0)).longValue();
		long den = ((Double)l.get(1)).longValue();
		return vf.rational(num, den);
	}
	
	private Type elementType(Type t) {
		return t.isTop() ? tf.valueType() : t.getElementType();
	}
	
	private Type keyType(Type t) {
		return t.isTop() ? tf.valueType() : t.getKeyType();
	}
	
	private Type valueType(Type t) {
		return t.isTop() ? tf.valueType() : t.getValueType();
	}

	@Override
	public IValue visitList(Type type) throws IOException {
		IListWriter w = vf.listWriter();
		List l = (List)stack.peek();
		for (Object e: l) {
			stack.push(e);
			w.append(read(elementType(type)));
			stack.pop();
		}
		return w.done();
	}

	@Override
	public IValue visitMap(Type type) throws IOException {
		// [ [k, v], ... ]
		IMapWriter w = vf.mapWriter();
		List l = (List)stack.peek();
		for (Object e: l) {
			List pair = (List)e;
			stack.push(pair.get(0));
			IValue k = read(keyType(type));
			stack.pop();
			stack.push(pair.get(1));
			IValue v = read(valueType(type));
			stack.pop();
			w.put(k, v);
		}
		return w.done();
	}

	@Override
	public IValue visitNumber(Type type) throws IOException {
		IValue value = null;
		String tag = tag();
		stack.push(contents());
		switch (tag) {
		case "int":
			value = visitInteger(type);
			break;
		case "real":
			value = visitReal(type);
			break;
		case "rat":
			value = visitRational(type);
			break;
		default:
			throw new IOException("invalid tag for num: " + tag);
		}
		stack.pop();
		return value;
	}

	@Override
	public IValue visitAlias(Type type) throws IOException {
		throw new AssertionError("alias normalization should happen in read()");
	}

	@Override
	public IValue visitFunction(Type type) throws IOException {
		throw new AssertionError("can not deserialize functions from JSON");
	}

	@Override
	public IValue visitSet(Type type) throws IOException {
		ISetWriter w = vf.setWriter();
		List l = (List)stack.peek();
		for (Object e: l) {
			stack.push(e);
			w.insert(read(elementType(type)));
			stack.pop();
		}
		return w.done();
	}

	@Override
	public IValue visitSourceLocation(Type type) throws IOException {
		String scheme = null;
		String authority = null;
		String path = null;
		String fragment = "";
		String query = "";
		int offset = -1;
		int length = -1;
		int beginLine = -1;
		int endLine = -1;
		int beginColumn = -1;
		int endColumn = -1;
		
		Map m = (Map)stack.peek();
		for (Object k: m.keySet()) {
			String name = (String)k;
			switch (name) {
			case "scheme":
				scheme = (String)m.get(name);
				break;
			case "authority":
				authority = (String)m.get(name);
				break;
			case "path":
				path = (String)m.get(name);
				break;
			case "fragment":
				fragment = (String)m.get(name);
				break;
			case "query":
				query = (String)m.get(name);
				break;
			case "offset":
				offset = ((Double)m.get(name)).intValue();
				break;
			case "length":
				length = ((Double)m.get(name)).intValue();
				break;
			case "beginLine":
				beginLine = ((Double)m.get(name)).intValue();
				break;
			case "endLine":
				endLine = ((Double)m.get(name)).intValue();
				break;
			case "beginColumn":
				beginColumn = ((Double)m.get(name)).intValue();
				break;
			case "endColumn":
				endColumn = ((Double)m.get(name)).intValue();
				break;
			default:
				throw new IOException(
						"invalid property name in SourceLocation serialization: "
								+ name);
			}
		}
		try {
		    ISourceLocation top = vf.sourceLocation(scheme, authority, path, query, fragment);
		    if (offset != -1 && length != -1 && beginLine != -1 && endLine != -1 && beginColumn != -1 && endColumn != -1) {
		        return vf.sourceLocation(top, offset, length, beginLine, endLine, beginColumn, endColumn);
		    }
		    else if (offset != -1 && length != -1) {
		        return vf.sourceLocation(top, offset, length);
		    }
		    else {
		        return top;
		    }
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}

	@Override
	public IValue visitString(Type type) throws IOException {
		return vf.string((String)stack.peek());
	}

	@Override
	public IValue visitNode(Type type) throws IOException {
		// ["name", [ ... ] ]

		List l = (List)stack.peek();
		
		String name = (String)l.get(0);
		int arity = ((Double)l.get(1)).intValue();
		
		List argsList = (List) l.get(2);
		
		IValue[] args = new IValue[arity];
		for (int i = 0; i < arity; i++) {
			stack.push(argsList.get(i));
			args[i] = read(tf.valueType());
			stack.pop();
		}
		
		Map<String, IValue> kwargs = null;
		if (l.size() > 3) {
			kwargs = new HashMap<>();
			Map kw = (Map)l.get(3);
			for (Object k: kw.keySet()) {
				String label = (String)k;
				stack.push(kw.get(label));
				kwargs.put(label, fixupTypeOfNodeValues(read(tf.valueType())));
				stack.pop();
			}
		}

		if (kwargs != null) {
			return vf.node(name, args, kwargs);
		}
		return vf.node(name, args);
	}
	
	private IValue fixupTypeOfNodeValues(IValue input) {
		return input.accept(new IValueVisitor<IValue, RuntimeException>() {

			@Override
			public IValue visitList(IList o) throws RuntimeException {
				List<IValue> elements = new ArrayList<IValue>(o.length());
				for (IValue e : o) {
					elements.add(e.accept(this));
				}
				IListWriter writer = vf.listWriter();
				writer.appendAll(elements);
				return writer.done();
			}

			@Override
			public IValue visitSet(ISet o) throws RuntimeException {
				List<IValue> elements = new ArrayList<IValue>(o.size());
				for (IValue e : o) {
					elements.add(e.accept(this));
				}
				ISetWriter writer = vf.setWriter();
				writer.insertAll(elements);
				return writer.done();
			}
			
			@Override
			public IValue visitTuple(ITuple o) throws RuntimeException {
				IValue[] elements = new IValue[o.arity()];
				Type[] types = new Type[o.arity()];
				for (int i = 0; i < elements.length; i++) {
					elements[i] = o.get(i).accept(this);
					types[i] = elements[i].getType();
				}

				return vf.tuple(elements);
			}

			@Override
			public IValue visitNode(INode o) throws RuntimeException {
				IValue[] children = new IValue[o.arity()];
				for (int i = 0; i < children.length; i++) {
					children[i] = o.get(i).accept(this);
				}
				if (o.mayHaveKeywordParameters()) {
					IWithKeywordParameters<? extends INode> okw = o.asWithKeywordParameters();
					Map<String, IValue> oldKwParams = okw.getParameters();
					Map<String, IValue> kwParams = new HashMap<>(oldKwParams.size());
					for (String key : oldKwParams.keySet()) {
						kwParams.put(key, oldKwParams.get(key).accept(this));

					}
					return vf.node(o.getName(), children, kwParams);
				}

				return vf.node(o.getName(), children);
			}

			@Override
			public IValue visitMap(IMap o) throws RuntimeException {
				Iterator<Entry<IValue,IValue>> entries = o.entryIterator();
				Map<IValue, IValue> newEntries = new HashMap<>(o.size());
				while (entries.hasNext()) {
					Entry<IValue, IValue> ent = entries.next();
					newEntries.put(ent.getKey().accept(this), ent.getValue().accept(this));
				}

				IMapWriter writer = vf.mapWriter();
				writer.putAll(newEntries);
				return writer.done();
			}

			@Override
			public IValue visitConstructor(IConstructor o)
					throws RuntimeException {
				throw new NotYetImplemented("Constructors are not yet implemented");
			}

			@Override
			public IValue visitString(IString o) throws RuntimeException {
				return o;
			}

			@Override
			public IValue visitReal(IReal o) throws RuntimeException {
				return o;
			}

			@Override
			public IValue visitRational(IRational o) throws RuntimeException {
				return o;
			}



			@Override
			public IValue visitSourceLocation(ISourceLocation o)
					throws RuntimeException {
				return o;
			}


			@Override
			public IValue visitInteger(IInteger o) throws RuntimeException {
				return o;
			}

			@Override
			public IValue visitBoolean(IBool boolValue) throws RuntimeException {
				return boolValue;
			}

			@Override
			public IValue visitExternal(IExternalValue externalValue)
					throws RuntimeException {
				return externalValue;
			}

			@Override
			public IValue visitDateTime(IDateTime o) throws RuntimeException {
				return o;
			}
		});
	}

	@Override
	public IValue visitConstructor(Type type) throws IOException {
		throw new NotYetImplemented("constructor types");
	}

	@Override
	public IValue visitAbstractData(Type type) throws IOException {
		// [ "name", [ ... ], { ... } }
		
		if (type == JSON) {
			return readPlainJSON();
		}

		List l = (List)stack.peek();
		String name = (String)l.get(0);
		int arity = ((Double)l.get(1)).intValue();

		Set<Type> ctors = ts.lookupConstructor(type, name);
		Type ctor = null;
		for (Type t: ctors) {
			if (t.getArity() == arity) {
				ctor = t;
				break;
			}
		}
		
		if (ctor == null) {
			throw new IOException("no constructor " + name + "/" + arity+ " in " + type);
		}
		
		List argsList = (List) l.get(2);
	
		IValue[] args = new IValue[arity];
		for (int i = 0; i < arity; i++) {
			stack.push(argsList.get(i));
			args[i] = read(ctor.getFieldType(i));
			stack.pop();
		}

		Map<String, IValue> kwargs = null;
		Map<String, Type> kwformals = ts.getKeywordParameters(ctor);
		
		if (kwformals.size() > 0 && l.size() > 3) {
			kwargs = new HashMap<>();
			Map kw = (Map)l.get(3);
			for (Object k: kw.keySet()) {
				String label = (String)k;
				Type kwType = kwformals.get(label);
				if (kwType == null) {
					// TODO: JV added this, kwType could be null
					kwType = tf.valueType();
				}
				stack.push(kw.get(label));
				kwargs.put(label, read(kwType));
				stack.pop();
			}
		}

		if (kwargs != null) {
			return vf.constructor(ctor, args, kwargs);
		}

		return vf.constructor(ctor, args);
	}

	
	private IValue readPlainJSON() throws IOException {
		return convertToIValue(stack.peek());
	}

	private IValue convertToIValue(Object obj) throws IOException {
		if (obj == null) {
			return vf.constructor(JSON_null);
		}
		if (obj instanceof Double) {
			return vf.constructor(JSON_number, vf.real(((Double)obj).doubleValue()));
		}
		if (obj instanceof Boolean) {
			return vf.constructor(JSON_boolean, vf.bool(((Boolean)obj).booleanValue()));
		}
		if (obj instanceof String) {
			return vf.constructor(JSON_string, vf.string((String)obj));
		}
		if (obj instanceof Map) {
			Map map = (Map)obj;
			if (map.keySet().size() == 1) {
				for (Object k: map.keySet()) {
					if (k.equals("#value")) {
						stack.push(map.get(k));
						IValue v = read(tf.valueType());
						stack.pop();
						return vf.constructor(JSON_ivalue, v);
					}
				}
			}
			IMapWriter w = vf.mapWriter();
			for (Object k: map.keySet()) {
				w.put(vf.string((String) k), convertToIValue(map.get(k)));	
			}
			return vf.constructor(JSON_object, w.done());
		}
		if (obj instanceof List) {
			IListWriter w = vf.listWriter();
			for (Object k: (List)obj) {
				w.append(convertToIValue(k));	
			}
			return vf.constructor(JSON_array, w.done());
		}

		throw new AssertionError("unhandled generic JSON object: " + obj);
	}

	@Override
	public IValue visitTuple(Type type) throws IOException {
		List l = (List)stack.peek();
		
		if (type.isTop()) {
			IValue[] args = new IValue[l.size()];
			for (int i = 0; i < l.size(); i++) {
				stack.push(l.get(i));
				args[i] = read(tf.valueType());
				stack.pop();
			}
			return vf.tuple(args);
		}

		IValue args[] = new IValue[type.getArity()];
		for (int i = 0; i < l.size(); i++) {
			stack.push(l.get(i));
			args[i] = read(type.getFieldType(i));
			stack.pop();
		}
		return vf.tuple(args);
	}

	@Override
	public IValue visitValue(Type type) throws IOException {
		String tag = tag();
		IValue value = null;
		stack.push(contents());
		switch (tag) {
		case "cons":
			value = visitNode(type);
			break;
		case "node":
			value = visitNode(type);
			break;
		case "int":
			value = visitInteger(type);
			break;
		case "rat":
			value = visitRational(type);
			break;
		case "real":
			value = visitReal(type);
			break;
		case "loc":
			value = visitSourceLocation(type);
			break;
		case "datetime":
			value = visitDateTime(type);
			break;
		case "bool":
			value = visitBool(type);
			break;
		case "list":
			value = visitList(type);
			break;
		case "set":
			value = visitSet(type);
			break;
		case "map":
			value = visitMap(type);
			break;
		case "str":
			value = visitString(type);
			break;
		case "tuple":
			value = visitTuple(type);
			break;
		default:
			throw new IOException("invalid tag for value: " + tag);
		}
		stack.pop();
		return value;
	}

	@Override
	public IValue visitDateTime(Type type) throws IOException {
		// {datetime: { }}
		int year = -1;
		int monthOfYear = -1;
		int dayOfMonth = -1;
				
		int hourOfDay = -1;
		int minuteOfHour = -1;
		int secondOfMinute = -1;
		int millisecondsOfSecond = -1;
		int timezoneOffsetHours = -99;
		int timezoneOffsetMinutes = -99;
		
		Map m = (Map)stack.peek();
		
		for (Object k: m.keySet()) {
			String fld = (String)k;
			int n = ((Double)m.get(fld)).intValue();
			switch (fld) {
			case "year": year = n; break;
			case "monthOfYear": monthOfYear = n; break;
			case "dayOfMonth": dayOfMonth = n; break;
			case "hourOfDay": hourOfDay = n; break;
			case "minuteOfHour": minuteOfHour = n; break;
			case "secondOfMinute": secondOfMinute = n; break;
			case "millisecondsOfSecond": millisecondsOfSecond = n; break;
			case "timezoneOffsetHours": timezoneOffsetHours = n; break;
			case "timezoneOffsetMinutes": timezoneOffsetMinutes = n; break;
			default: throw new IOException("invalid field for date time: " + fld);
			}
		}

		if (year != -1 && monthOfYear != -1 && dayOfMonth != -1 && hourOfDay != -1 
				&& minuteOfHour != -1 && secondOfMinute != -1 && millisecondsOfSecond != -1
				&& timezoneOffsetHours != -99 && timezoneOffsetMinutes != -99) {
			return vf.datetime(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute, millisecondsOfSecond, timezoneOffsetHours, timezoneOffsetMinutes);
		}
		if (year != -1 && monthOfYear != -1 && dayOfMonth != -1 && hourOfDay != -1 
				&& minuteOfHour != -1 && secondOfMinute != -1 && millisecondsOfSecond != -1) {
			return vf.datetime(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute, millisecondsOfSecond);
		}
		if (year != -1 && monthOfYear != -1 && dayOfMonth != -1) {
			return vf.date(year, monthOfYear, dayOfMonth);
		}
		throw new IOException("insufficient fields for making a datetime value");
	}

	@Override
	public IValue visitVoid(Type type) throws IOException {
		throw new AssertionError("cannot read values of type void");
	}

	@Override
	public IValue visitBool(Type type) throws IOException {
		return vf.bool((Boolean)stack.peek());
	}

	@Override
	public IValue visitParameter(Type type) throws IOException {
		throw new AssertionError("parameter types should have been bound");
	}

	@Override
	public IValue visitExternal(Type type) throws IOException {
		if (type instanceof NonTerminalType) {
			return RascalValueFactory.Tree.accept(this);
		}
		throw new IOException("cannot deserialize external values");
	}

}