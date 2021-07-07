/*******************************************************************************
 * /******************************************************************************* Copyright (c)
 * 2009-2020 CWI, NWO-I CWI All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: * Paul Klint - Paul.Klint@cwi.nl - CWI * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl -
 * CWI * Arnold Lankamp - Arnold.Lankamp@cwi.nl * Davy Landman - Davy.Landman@cwi.nl * Michael
 * Steindorfer - Michael.Steindorfer@cwi.nl - CWI
 *******************************************************************************/

package org.rascalmpl.library;

import static org.rascalmpl.values.RascalValueFactory.TYPE_STORE_SUPPLIER;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


import org.apache.commons.lang.CharSetUtils;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.interpreter.utils.LimitedResultWriter.IOLimitReachedException;
import org.rascalmpl.repl.LimitedLineWriter;
import org.rascalmpl.types.TypeReifier;
import org.rascalmpl.unicode.UnicodeDetector;
import org.rascalmpl.unicode.UnicodeOffsetLengthReader;
import org.rascalmpl.unicode.UnicodeOutputStreamWriter;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChangeType;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationChanged;
import org.rascalmpl.uri.ISourceLocationWatcher.ISourceLocationType;
import org.rascalmpl.uri.LogicalMapResolver;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.uri.UnsupportedSchemeException;
import org.rascalmpl.util.DateTimeConversions;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.RascalValueFactory;
import org.rascalmpl.values.functions.IFunction;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.ProductionAdapter;
import org.rascalmpl.values.parsetrees.SymbolAdapter;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.values.parsetrees.visitors.TreeVisitor;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IDateTime;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.IMapWriter;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.IRational;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISetWriter;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.io.StandardTextReader;
import io.usethesource.vallang.io.StandardTextWriter;
import io.usethesource.vallang.io.binary.stream.IValueInputStream;
import io.usethesource.vallang.io.binary.stream.IValueOutputStream;
import io.usethesource.vallang.io.binary.stream.IValueOutputStream.CompressionRate;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class Prelude {
	private static final int FILE_BUFFER_SIZE = 8 * 1024;
	protected final IValueFactory values;
	protected final IRascalValueFactory rascalValues;
	private final Random random;

	private final boolean trackIO = System.getenv("TRACKIO") != null;
	private final PrintWriter out;
	private final TypeStore store;

	public Prelude(IValueFactory values, IRascalValueFactory rascalValues, PrintWriter out, TypeStore store) {
		super();

		this.values = values;
		this.rascalValues = rascalValues;
		this.store = store;
		this.out = out;
		this.tr = new TypeReifier(values);
		random = new Random();
	}

	private IValue createRandomValue(Type t, int depth, int width) {
		return t.randomValue(random, values, new TypeStore(), Collections.emptyMap(), depth, width);
	}


	/*
	 * Boolean
	 */


	public IValue arbBool() // get an arbitrary boolean value.}
	{
		return values.bool(random.nextInt(2) == 1);
	}

	/*
	 * DateTime
	 */
	public IValue now()
	// @doc{Get the current datetime.}
	{
		return values.datetime(Instant.now().toEpochMilli());
	}

	public IValue createDate(IInteger year, IInteger month, IInteger day)
	// @doc{Create a new date.}
	{
		return values.date(year.intValue(), month.intValue(), day.intValue());
	}

	public IValue createTime(IInteger hour, IInteger minute, IInteger second, IInteger millisecond)
	// @doc{Create a new time.}
	{
		return values.time(hour.intValue(), minute.intValue(), second.intValue(), millisecond.intValue());
	}

	public IValue createTime(IInteger hour, IInteger minute, IInteger second, IInteger millisecond,
		IInteger timezoneHourOffset, IInteger timezoneMinuteOffset)
	// @doc{Create a new time with the given numeric timezone offset.}
	{
		return values.time(hour.intValue(), minute.intValue(), second.intValue(), millisecond.intValue(),
			timezoneHourOffset.intValue(), timezoneMinuteOffset.intValue());
	}

	public IValue createDateTime(IInteger year, IInteger month, IInteger day, IInteger hour, IInteger minute,
		IInteger second, IInteger millisecond)
	// @doc{Create a new datetime.}
	{
		return values.datetime(year.intValue(), month.intValue(), day.intValue(), hour.intValue(), minute.intValue(),
			second.intValue(), millisecond.intValue());
	}

	public IValue createDateTime(IInteger year, IInteger month, IInteger day, IInteger hour, IInteger minute,
		IInteger second, IInteger millisecond, IInteger timezoneHourOffset, IInteger timezoneMinuteOffset)
	// @doc{Create a new datetime with the given numeric timezone offset.}
	{
		return values.datetime(year.intValue(), month.intValue(), day.intValue(), hour.intValue(), minute.intValue(),
			second.intValue(), millisecond.intValue(), timezoneHourOffset.intValue(), timezoneMinuteOffset.intValue());
	}


	public IDateTime arbDateTime() {
		return (IDateTime) createRandomValue(TypeFactory.getInstance().dateTimeType(), 5, 5);
	}

	public IValue joinDateAndTime(IDateTime date, IDateTime time)
	// @doc{Create a new datetime by combining a date and a time.}
	{
		return values.datetime(date.getYear(), date.getMonthOfYear(), date.getDayOfMonth(), time.getHourOfDay(),
			time.getMinuteOfHour(), time.getSecondOfMinute(), time.getMillisecondsOfSecond(),
			time.getTimezoneOffsetHours(), time.getTimezoneOffsetMinutes());
	}

	public IValue splitDateTime(IDateTime dt)
	// @doc{Split an existing datetime into a tuple with the date and the time.}
	{
		return values.tuple(values.date(dt.getYear(), dt.getMonthOfYear(), dt.getDayOfMonth()),
			values.time(dt.getHourOfDay(), dt.getMinuteOfHour(), dt.getSecondOfMinute(), dt.getMillisecondsOfSecond(),
				dt.getTimezoneOffsetHours(), dt.getTimezoneOffsetMinutes()));
	}


	public IValue incrementYears(IDateTime dt, IInteger n)
	// @doc{Increment the years by a given amount.}
	{
		return incrementDate(dt, ChronoUnit.YEARS, "years", n);
	}

	public IValue incrementMonths(IDateTime dt, IInteger n)
	// @doc{Increment the months by a given amount.}
	{
		return incrementDate(dt, ChronoUnit.MONTHS, "months", n);
	}

	public IValue incrementDays(IDateTime dt, IInteger n)
	// @doc{Increment the days by a given amount.}
	{
		return incrementDate(dt, ChronoUnit.DAYS, "days", n);
	}


	private IValue incrementDTField(IDateTime dt, ChronoUnit field, IInteger amount) {
		Temporal actualDt = DateTimeConversions.dateTimeToJava(dt);
		Temporal result = actualDt.plus(amount.longValue(), field);

		if (dt.isDate()) {
			return temporalToDate(result);
		}
		else if (dt.isTime()) {
			return temporalToTime(result);
		}
		return temporalToDateTime((OffsetDateTime) result);
	}
	private IDateTime temporalToDate(TemporalAccessor t) {
		return DateTimeConversions.temporalToDate(values, (LocalDate)t);
	}


	private IDateTime temporalToTime(TemporalAccessor t) {
		if (t instanceof OffsetTime) {
			return DateTimeConversions.temporalToTime(values, (OffsetTime)t);
		}
		return DateTimeConversions.temporalToTime(values, (LocalTime)t);
	}


	private IDateTime temporalToDateTime(OffsetDateTime t) {
		return DateTimeConversions.temporalToDateTime(values, t);
	}

	private IValue incrementTime(IDateTime dt, ChronoUnit field, String fieldName, IInteger amount) {
		if (dt.isDate())
			throw RuntimeExceptionFactory
				.invalidUseOfDateException("Cannot increment the " + fieldName + " on a date value.");

		return incrementDTField(dt, field, amount);
	}

	private IValue incrementDate(IDateTime dt, ChronoUnit field, String fieldName, IInteger amount) {
		if (dt.isTime())
			throw RuntimeExceptionFactory
				.invalidUseOfDateException("Cannot increment the " + fieldName + " on a time value.");

		return incrementDTField(dt, field, amount);
	}

	public IValue incrementHours(IDateTime dt, IInteger n)
	// @doc{Increment the hours by a given amount.}
	{
		return incrementTime(dt, ChronoUnit.HOURS, "hours", n);
	}

	public IValue incrementMinutes(IDateTime dt, IInteger n)
	// @doc{Increment the minutes by a given amount.}
	{
		return incrementTime(dt, ChronoUnit.MINUTES, "minutes", n);
	}

	public IValue incrementSeconds(IDateTime dt, IInteger n)
	// @doc{Increment the seconds by a given amount.}
	{
		return incrementTime(dt, ChronoUnit.SECONDS, "seconds", n);
	}

	public IValue incrementMilliseconds(IDateTime dt, IInteger n)
	// @doc{Increment the milliseconds by a given amount.}
	{
		return incrementTime(dt, ChronoUnit.MILLIS, "milliseconds", n);
	}

	public IValue decrementYears(IDateTime dt, IInteger n)
	// @doc{Decrement the years by a given amount.}
	{
		return incrementDate(dt, ChronoUnit.YEARS, "years", n.negate());
	}

	public IValue decrementMonths(IDateTime dt, IInteger n)
	// @doc{Decrement the months by a given amount.}
	{
		return incrementDate(dt, ChronoUnit.MONTHS, "months", n.negate());
	}

	public IValue decrementDays(IDateTime dt, IInteger n)
	// @doc{Decrement the days by a given amount.}
	{
		return incrementDate(dt, ChronoUnit.DAYS, "days", n.negate());
	}

	public IValue decrementHours(IDateTime dt, IInteger n)
	// @doc{Decrement the hours by a given amount.}
	{
		return incrementTime(dt, ChronoUnit.HOURS, "hours", n.negate());
	}

	public IValue decrementMinutes(IDateTime dt, IInteger n)
	// @doc{Decrement the minutes by a given amount.}
	{
		return incrementTime(dt, ChronoUnit.MINUTES, "minutes", n.negate());
	}

	public IValue decrementSeconds(IDateTime dt, IInteger n)
	// @doc{Decrement the seconds by a given amount.}
	{
		return incrementTime(dt, ChronoUnit.SECONDS, "seconds", n.negate());
	}

	public IValue decrementMilliseconds(IDateTime dt, IInteger n)
	// @doc{Decrement the milliseconds by a given amount.}
	{
		return incrementTime(dt, ChronoUnit.MILLIS, "milliseconds", n.negate());
	}

	public IValue createDurationInternal(IDateTime dStart, IDateTime dEnd) {
		// dStart and dEnd both have to be dates, times, or datetimes
		Temporal startTemp = DateTimeConversions.dateTimeToJava(dStart);
		Temporal endTemp = DateTimeConversions.dateTimeToJava(dEnd);

		if (startTemp instanceof LocalDate) {
			if (endTemp instanceof LocalDate) {
				Period duration = Period.between((LocalDate) startTemp, (LocalDate) endTemp).normalized();

				return values.tuple(values.integer(duration.getYears()), values.integer(duration.getMonths()),
					values.integer(duration.getDays()), values.integer(0), values.integer(0), values.integer(0),
					values.integer(0));
			}
			else if (endTemp instanceof OffsetTime) {
				throw RuntimeExceptionFactory.invalidUseOfTimeException(
					"Cannot determine the duration between a date with no time and a time with no date.");
			}
			else {
				throw RuntimeExceptionFactory.invalidUseOfDateTimeException(
					"Cannot determine the duration between a date with no time and a datetime.");
			}
		}
		else if (startTemp instanceof OffsetTime) {
			if (endTemp instanceof OffsetTime) {
				Duration duration = Duration.between(startTemp, endTemp);
				return values.tuple(values.integer(0), values.integer(0), values.integer(0),
					values.integer(duration.toHours()), values.integer(duration.toMinutes() % 60),
					values.integer(duration.getSeconds() % 60), values.integer(duration.toMillis() % 1000));
			}
			else if (dEnd.isDate()) {
				throw RuntimeExceptionFactory.invalidUseOfDateException(
					"Cannot determine the duration between a time with no date and a date with no time.");
			}
			else {
				throw RuntimeExceptionFactory.invalidUseOfDateTimeException(
					"Cannot determine the duration between a time with no date and a datetime.");
			}
		}
		else {
			if (dEnd.isDateTime()) {
				return values.tuple(values.integer(ChronoUnit.YEARS.between(startTemp, endTemp)),
					values.integer(ChronoUnit.MONTHS.between(startTemp, endTemp) % 12),
					values.integer(ChronoUnit.DAYS.between(startTemp, endTemp) % 31), // TODO: this is broken, day of
																						// month in durations ??
					values.integer(ChronoUnit.HOURS.between(startTemp, endTemp) % 24),
					values.integer(ChronoUnit.MINUTES.between(startTemp, endTemp) % 60),
					values.integer(ChronoUnit.SECONDS.between(startTemp, endTemp) % 60),
					values.integer(ChronoUnit.MILLIS.between(startTemp, endTemp) % 1000));
			}
			else if (dEnd.isDate()) {
				throw RuntimeExceptionFactory.invalidUseOfDateException(
					"Cannot determine the duration between a datetime and a date with no time.");
			}
			else {
				throw RuntimeExceptionFactory.invalidUseOfTimeException(
					"Cannot determine the duration between a datetime and a time with no date.");
			}
		}
	}

	public IValue parseDate(IString inputDate, IString formatString)
	// @doc{Parse an input date given as a string using the given format string}
	{
		return parseDateTime(values, inputDate, prepareFormatter(formatString));
	}

	public IValue parseDateInLocale(IString inputDate, IString formatString, IString locale)
	// @doc{Parse an input date given as a string using a specific locale and format string}
	{
		return parseDateTime(values, inputDate, prepareFormatterLocale(formatString, locale));
	}

	public IValue parseTime(IString inputTime, IString formatString)
	// @doc{Parse an input time given as a string using the given format string}
	{
		return parseDateTime(values, inputTime, prepareFormatter(formatString));
	}

	public IValue parseTimeInLocale(IString inputTime, IString formatString, IString locale)
	// @doc{Parse an input time given as a string using a specific locale and format string}
	{
		return parseDateTime(values, inputTime, prepareFormatterLocale(formatString, locale));
	}

	public IString printSymbol(IConstructor symbol, IBool withLayout) {
		return values.string(SymbolAdapter.toString(symbol, withLayout.getValue()));
	}

	public IValue parseDateTime(IString inputDateTime, IString formatString) {
		return parseDateTime(values, inputDateTime, formatString);
	}

	static public IValue parseDateTime(IValueFactory values, IString inputDateTime, IString formatString)
	// @doc{Parse an input datetime given as a string using the given format string}
	{
		return parseDateTime(values, inputDateTime, prepareFormatter(formatString));
	}
	
	public IValue parseDateTimeInLocale(IString inputDateTime, IString formatString, IString locale) 
	//@doc{Parse an input datetime given as a string using a specific locale and format string}
	{
		return parseDateTime(values, inputDateTime, prepareFormatterLocale(formatString, locale));
	}

	private static IValue parseDateTime(IValueFactory values, IString input, DateTimeFormatter format) {
		try {
			return DateTimeConversions.temporalToIValue(values, format.parse(input.getValue()));
		} catch (IllegalArgumentException | DateTimeParseException e) {
			throw RuntimeExceptionFactory.dateTimeParsingError("Cannot parse input: " + input.getValue() + " with formatter: " + format + " error: " + e.getMessage());
		}

	}


	public IValue printDate(IDateTime inputDate, IString formatString) 
	//@doc{Print an input date using the given format string}
	{
		return printDateTime(inputDate, prepareFormatter(formatString));
	}

	public IValue printDate(IDateTime inputDate) 
	//@doc{Print an input date using a default format string}
	{
		return printDate(inputDate, values.string("yyyy-MM-dd"));
	}
	
	public IValue printDateInLocale(IDateTime inputDate, IString formatString, IString locale) 
	//@doc{Print an input date using a specific locale and format string}
	{
		return printDateTime(inputDate, prepareFormatterLocale(formatString, locale));
	}

	public IValue printDateInLocale(IDateTime inputDate, IString locale) 
	//@doc{Print an input date using a specific locale and a default format string}
	{
		return printDateInLocale(inputDate, values.string("yyyy-MM-dd"), locale);
	}

	public IValue printTime(IDateTime inputTime, IString formatString) 
	//@doc{Print an input time using the given format string}
	{
		return printDateTime(inputTime, prepareFormatter(formatString));
	}
	
	public IValue printTime(IDateTime inputTime) 
	//@doc{Print an input time using a default format string}
	{
		return printTime(inputTime, values.string("HH:mm:ss.SSSZZZZZ"));
	}
	
	public IValue printTimeInLocale(IDateTime inputTime, IString formatString, IString locale) 
	//@doc{Print an input time using a specific locale and format string}
	{
		return printDateTime(inputTime, prepareFormatterLocale(formatString, locale));
	}

	public IValue printTimeInLocale(IDateTime inputTime, IString locale) 
	//@doc{Print an input time using a specific locale and a default format string}
	{
		return printDateTimeInLocale(inputTime, values.string("HH:mm:ss.SSSZZZZZ"), locale);
	}

	public IValue printDateTime(IDateTime inputDateTime, IString formatString) 
	//@doc{Print an input datetime using the given format string}
	{
		return printDateTime(inputDateTime, prepareFormatter(formatString));
	}
	
	private IString printDateTime(IDateTime input, DateTimeFormatter fmt) {
		try {
			return values.string(fmt.format(DateTimeConversions.dateTimeToJava(input)));
		} catch (RuntimeException iae) {
			throw RuntimeExceptionFactory.dateTimePrintingError("Cannot print datetime " + input + " using formatter: " + fmt);
		}
	}

	public IValue printDateTime(IDateTime inputDateTime) 
	//@doc{Print an input datetime using a default format string}
	{
		return printDateTime(inputDateTime, values.string("yyyy-MM-dd HH:mm:ss.SSSZZZZZ"));
	}
	
	public IValue printDateTimeInLocale(IDateTime inputDateTime, IString formatString, IString locale) 
	//@doc{Print an input datetime using a specific locale and format string}
	{
		return printDateTime(inputDateTime, prepareFormatterLocale(formatString, locale));
	}

	private static DateTimeFormatter prepareFormatter(IString formatString) {
		return DateTimeFormatter.ofPattern(formatString.getValue());
	}
	
	private static DateTimeFormatter prepareFormatterLocale(IString formatString, IString locale) {
		return prepareFormatterLocale(formatString.getValue(), locale.getValue());
	}

	private static DateTimeFormatter prepareFormatterLocale(String formatString, String locale) {
		return DateTimeFormatter.ofPattern(formatString)
			.withLocale(Locale.forLanguageTag(locale));
	}

	public IValue printDateTimeInLocale(IDateTime inputDateTime, IString locale) 
	//@doc{Print an input datetime using a specific locale and a default format string}
	{
		return printDateTimeInLocale(inputDateTime, values.string("yyyy-MM-dd HH:mm:ss.SSS ZZZZZ"), locale);
	}
	
    public IValue daysDiff(IDateTime dtStart, IDateTime dtEnd)
    //@doc{Increment the years by a given amount.}
	{
		if (!(dtStart.isTime() || dtEnd.isTime())) {
			Temporal start = DateTimeConversions.dateTimeToJava(dtStart);
			Temporal end = DateTimeConversions.dateTimeToJava(dtEnd);
			return values.integer(ChronoUnit.DAYS.between(start, end));
		}
		throw RuntimeExceptionFactory.invalidUseOfTimeException("Both inputs must include dates.");
	}

    /*
	 * Graph
	 */
	
	private Map<IValue,Distance> distance;
	private Map<IValue, IValue> pred;
	private Set<IValue> settled;
	private PriorityQueue<IValue> Q;
	private int MAXDISTANCE = 10000;
	
	private Map<IValue, LinkedList<IValue>> adjacencyList;
	
	private void buildAdjacencyListAndDistance(ISet G){
		adjacencyList = new HashMap<> ();
		distance = new HashMap<>();
		
		for(IValue v : G){
			ITuple tup = (ITuple) v;
			IValue from = tup.get(0);
			IValue to = tup.get(1);
			
			if(distance.get(from) == null)
				distance.put(from, new Distance(MAXDISTANCE));
			if(distance.get(to) == null)
				distance.put(to, new Distance(MAXDISTANCE));
			
			LinkedList<IValue> adjacencies = adjacencyList.computeIfAbsent(from, (k) -> new LinkedList<>());
			adjacencies.add(to);
			adjacencyList.put(from, adjacencies);
		}
	}
	
	public IValue shortestPathPair(ISet G, IValue From, IValue To){
		buildAdjacencyListAndDistance(G);
		IValue start = From;
		distance.put(start, new Distance(0));
		
		pred = new HashMap<>();
		settled = new HashSet<>();
		Q = new PriorityQueue<>(G.size(), new NodeComparator(distance));
		Q.add(start);
		
		while(!Q.isEmpty()){
			IValue u = Q.remove();
			if(u.equals(To)) {	
				return extractPath(start, u);
			}
			settled.add(u);
			relaxNeighbours(u);
		}
		return values.list();
	}
	
	private void relaxNeighbours(IValue u){
		LinkedList<IValue> adjacencies = adjacencyList.get(u);
		if(adjacencies != null) {
			for(IValue v : adjacencyList.get(u)){
				if(!settled.contains(v)){
					Distance dv = distance.get(v);
					Distance du = distance.get(u);
					if(dv.intval > du.intval + 1){  // 1 is default weight of each edge
						dv.intval = du.intval + 1;
						pred.put(v,u);
						Q.add(v);
					}
				}
			}
		}
	}
	
	private IList extractPath(IValue start, IValue u){
		IListWriter w = values.listWriter();
		
		if(!start.equals(u)){
			w.insert(u);
			while(!pred.get(u).equals(start)){
				u = pred.get(u);
				w.insert(u);
			}
		}
		w.insert(start);
		return w.done();
	}
	
	// REFLECT -- copy in {@link PreludeCompiled}
	public void print(IValue arg){
		PrintWriter currentOutStream = out;
		
		try{
			if(arg.getType().isString()){
			    ((IString) arg).write(currentOutStream);
			}
			else if(arg.getType().isSubtypeOf(RascalValueFactory.Tree)){
				currentOutStream.print(TreeAdapter.yield((IConstructor) arg));
			}
			else if (arg.getType().isSubtypeOf(RascalValueFactory.Type)) {
				currentOutStream.print(SymbolAdapter.toString((IConstructor) ((IConstructor) arg).get("symbol"), false));
			}
			else{
				currentOutStream.print(arg.toString());
			}
		}
        catch (IOException e) {
            throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
        }
		finally {
			currentOutStream.flush();
		}
	}
	
	// REFLECT -- copy in {@link PreludeCompiled}
	public void iprint(IValue arg, IInteger lineLimit){
		StandardTextWriter w = new StandardTextWriter(true, 2);
		Writer output = out;
		if (lineLimit.signum() > 0) {
		    output = new LimitedLineWriter(output, lineLimit.longValue());
		}
		
		try {
		    w.write(arg, output);
		} 
	    catch (IOLimitReachedException e) {
	        // ignore, it's what we wanted
	    }
		catch (IOException e) {
			RuntimeExceptionFactory.io(values.string("Could not print indented value"));
		}
		finally {
		    try {
		        output.flush();
		        output.close();
		    }
		    catch (IOException e) {
		    }
		}
	}
	
	// REFLECT -- copy in {@link PreludeCompiled}
	public void iprintToFile(ISourceLocation sloc, IValue arg) {
		StandardTextWriter w = new StandardTextWriter(true, 2);
		StringWriter sw = new StringWriter();

		try {
			w.write(arg, sw);
			writeFile(sloc, values.list(values.string(sw.toString())));
		} catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));		
		}
	}

	public IString iprintToString(IValue arg) {
		StandardTextWriter w = new StandardTextWriter(true, 2);
		StringWriter sw = new StringWriter();

		try {
			w.write(arg, sw);
			return values.string(sw.toString());
		} catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));		
		}
	}
	
	// REFLECT -- copy in {@link PreludeCompiled}
	public void iprintln(IValue arg, IInteger lineLimit){
	    iprint(arg, lineLimit);
	    out.println();
	    out.flush();
	}
	
	// REFLECT -- copy in {@link PreludeCompiled}
	public void println() {
		out.println();
		out.flush();
	}
	
	// REFLECT -- copy in {@link PreludeCompiled}
	public void println(IValue arg){
		PrintWriter currentOutStream = out;
		
		try{
			if(arg.getType().isString()){
			    ((IString) arg).write(currentOutStream);
			}
			else if(arg.getType().isSubtypeOf(RascalValueFactory.Tree)){
				currentOutStream.print(TreeAdapter.yield((IConstructor) arg));
			}
			else if (arg.getType().isSubtypeOf(RascalValueFactory.Type)) {
				currentOutStream.print(SymbolAdapter.toString((IConstructor) ((IConstructor) arg).get("symbol"), false));
			}
			else{
				currentOutStream.print(arg.toString());
			}
			currentOutStream.println();
		}
		catch (IOException e) {
		    throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
		finally {
			currentOutStream.flush();
		}
	}
	
	// REFLECT -- copy in {@link PreludeCompiled}
	public void rprintln(IValue arg){
		PrintWriter currentOutStream = out;
		
		try {
			currentOutStream.print(arg.toString());
			currentOutStream.println();
		}
		finally {
			currentOutStream.flush();
		}
	}
	
	// REFLECT -- copy in {@link PreludeCompiled}
	public void rprint(IValue arg){
		PrintWriter currentOutStream = out;
		
		try {
			currentOutStream.print(arg.toString());
		}
		finally {
			currentOutStream.flush();
		}
	}

	public IValue exists(ISourceLocation sloc) {
		IValue result =  values.bool(URIResolverRegistry.getInstance().exists(sloc));
		if(trackIO) System.err.println("exists: " + sloc + " => " + result);
		return result;
	}
	
	public IValue lastModified(ISourceLocation sloc) {
		try {
		    IValue result = values.datetime(URIResolverRegistry.getInstance().lastModified(sloc));
		    if(trackIO) System.err.println("lastModified: " + sloc + " => " + result);
			return result;
		} catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}

	public IValue created(ISourceLocation sloc) {
		try {
		    IValue result = values.datetime(URIResolverRegistry.getInstance().created(sloc));
		    if(trackIO) System.err.println("lastModified: " + sloc + " => " + result);
			return result;
		} catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}
	
	public void setLastModified(ISourceLocation sloc, IDateTime timestamp) {
	    setLastModified(sloc, timestamp.getInstant());
	}
	
	private void setLastModified(ISourceLocation sloc, long timestamp) {
        try {
            URIResolverRegistry.getInstance().setLastModified(sloc, timestamp);
        }
        catch (IOException e) {
            throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
        }
    }
	
	public IBool isDirectory(ISourceLocation sloc) {
		return values.bool(URIResolverRegistry.getInstance().isDirectory(sloc));
	}
	
	public IBool isFile(ISourceLocation sloc) {
		return values.bool(URIResolverRegistry.getInstance().isFile(sloc));
	}
	
	public void remove(ISourceLocation sloc, IBool recursive) {
		try {
			URIResolverRegistry.getInstance().remove(sloc, recursive.getValue());
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}
	
	public void mkDirectory(ISourceLocation sloc) {
	  try {
	    URIResolverRegistry.getInstance().mkDirectory(sloc);
	  }
	  catch (IOException e) {
	    throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
	  }
	}
	
	public IList listEntries(ISourceLocation sloc) {
		try {
			String [] entries = URIResolverRegistry.getInstance().listEntries(sloc);
			if (entries == null) {
			    throw RuntimeExceptionFactory.illegalArgument(sloc);
			}
			IListWriter w = values.listWriter();
			for(String entry : entries) {
				w.append(values.string(entry));
			}
			return w.done();
		} catch (FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		} catch (UnsupportedSchemeException e) {
		    throw RuntimeExceptionFactory.schemeNotSupported(sloc);
		} catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		} 
	}
	
	public ISet charsets() {
		ISetWriter w = values.setWriter();
		for (String s : Charset.availableCharsets().keySet()) {
			w.insert(values.string(s));
		}
		return w.done();
	} 
	
	public IString readFile(ISourceLocation sloc){
	    return readFile(values, trackIO, sloc);
	}
	
	static public IString readFile(IValueFactory values, boolean trackIO, ISourceLocation sloc){
		if(trackIO) System.err.println("readFile: " + sloc);
		try (Reader reader = URIResolverRegistry.getInstance().getCharacterReader(sloc);){
			return values.string(consumeInputStream(reader));
		} 
		catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}
	
	public IString readFileEnc(ISourceLocation sloc, IString charset){
		if(trackIO) System.err.println("readFileEnc: " + sloc);
		try (Reader reader = URIResolverRegistry.getInstance().getCharacterReader(sloc, charset.getValue())){
			return values.string(consumeInputStream(reader));
		} 
		catch (FileNotFoundException e) {
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		} 
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}

	public static String consumeInputStream(Reader in) throws IOException {
		StringBuilder res = new StringBuilder();
		char[] chunk = new char[FILE_BUFFER_SIZE];
		int read;
		while ((read = in.read(chunk, 0, chunk.length)) != -1) {
		    res.append(chunk, 0, read);
		}
		return res.toString();
	}
	
	public IValue md5HashFile(ISourceLocation sloc){
		byte[] hash;
		
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            boolean useInputStream = !URIResolverRegistry.getInstance().supportsReadableFileChannel(sloc);
            if (!useInputStream) {
                try (FileChannel file = URIResolverRegistry.getInstance().getReadableFileChannel(sloc)) {
                    ByteBuffer contents = null;
                    if (file.size() > FILE_BUFFER_SIZE) {
                        try {
                            contents = file.map(MapMode.READ_ONLY, 0, file.size());
                        } catch (IOException e) {
                            useInputStream = true;
                            contents = null;
                        }
                    }
                    else {
                        contents = ByteBuffer.allocate((int)file.size());
                        file.read(contents);
                        contents.flip();
                    }
                    if (contents != null) {
                        md.update(contents);
                    }
                }
            }
            if (useInputStream) {
                try (InputStream in = URIResolverRegistry.getInstance().getInputStream(sloc)) {
                    byte[] buf = new byte[FILE_BUFFER_SIZE];
                    int count;

                    while((count = in.read(buf, 0, buf.length)) != -1){
                        md.update(buf, 0, count);
                    }
                }
            }
			
			hash = md.digest();
		}catch(FileNotFoundException fnfex){
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		}catch(IOException ioex){
			throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()));
		} catch (NoSuchAlgorithmException e) {
			throw RuntimeExceptionFactory.io(values.string("Cannot load MD5 digest algorithm"));
		}
        
        StringBuffer result = new StringBuffer(hash.length * 2);
        for (int i = 0; i < hash.length; i++) {
            result.append(Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1));
        }
        return values.string(result.toString());

	}

	public IValue md5Hash(IValue value){
		try {
			final MessageDigest md = MessageDigest.getInstance("MD5");
			final StandardTextWriter writer = new StandardTextWriter();
			Charset UTF8 = Charset.forName("UTF-8");
			writer.write(value, new Writer() {
				@Override
				public void write(char[] cbuf, int off, int len) throws IOException {
					CharBuffer cb = CharBuffer.wrap(cbuf, off, len);
					ByteBuffer bb = UTF8.encode(cb); 
					md.update(bb);
				}

				@Override
				public void flush() throws IOException { }

				@Override
				public void close() throws IOException { }
			});

			byte[] hash = md.digest();

			StringBuffer result = new StringBuffer(hash.length * 2);
        	for (int i = 0; i < hash.length; i++) {
            	result.append(Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1));
        	}
			
        	return values.string(result.toString());
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
		catch (NoSuchAlgorithmException e) {
			throw RuntimeExceptionFactory.io(values.string("no such algorithm: " + e.getMessage()));
		}
	}
	
	public void copy(ISourceLocation source, ISourceLocation target, IBool recursive, IBool overwrite) {
		try {
			URIResolverRegistry.getInstance().copy(source, target, recursive.getValue(), overwrite.getValue());
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}

	public void move(ISourceLocation source, ISourceLocation target, IBool overwrite) {
		try {
			URIResolverRegistry.getInstance().rename(source, target, overwrite.getValue());
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}

	public void touch(ISourceLocation sloc) {
	    if (URIResolverRegistry.getInstance().exists(sloc)) {
	        setLastModified(sloc, System.currentTimeMillis());
	    }
	    else {
	        writeFile(sloc, values.list(values.string("")));
	    }
	}
	
	public void writeFile(ISourceLocation sloc, IList V) {
		writeFile(sloc, V, false);
	}
	
	public void writeFileEnc(ISourceLocation sloc, IString charset, IList V) {
		writeFileEnc(sloc, charset, V, false);
	}
	
	private void writeFile(ISourceLocation sloc, IList V, boolean append){
		if(trackIO) System.err.println("writeFile: " + sloc);
		IString charset = values.string("UTF8");
		if (append) {
			charset = detectCharSet(sloc);
		}
		
		writeFileEnc(sloc, charset, V, append);
	}

	private IString detectCharSet(ISourceLocation sloc) {
		IString charset;
		// in case the file already has a encoding, we have to correctly append that.
		Charset detected = null;
		try (InputStream in = URIResolverRegistry.getInstance().getInputStream(sloc);){
			detected = URIResolverRegistry.getInstance().getCharset(sloc);
			if (detected == null) {
				
				detected = UnicodeDetector.estimateCharset(in);
			}
		} 
		catch (FileNotFoundException fnfex) {
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		} 
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
		
		if (detected != null)
			charset = values.string(detected.name());
		else {
			charset = values.string(Charset.defaultCharset().name());
		}
		return charset;
	}
	
	public IBool canEncode(IString charset) {
		return values.bool(Charset.forName(charset.getValue()).canEncode());
	}
	
	private void writeFileEnc(ISourceLocation sloc, IString charset, IList V, boolean append){
		URIResolverRegistry reg = URIResolverRegistry.getInstance();

		if (!Charset.forName(charset.getValue()).canEncode()) {
		    throw RuntimeExceptionFactory.illegalArgument(charset);
		}
		
		Reader prefix = null;
		Reader postfix = null;
		
		try {
			sloc = reg.logicalToPhysical(sloc);

			if (reg.supportsInputScheme(sloc.getScheme())) {
				if (sloc.hasOffsetLength()) {
					prefix = new UnicodeOffsetLengthReader(reg.getCharacterReader(sloc.top(), charset.getValue()), 0, sloc.getOffset() + ( append ? sloc.getLength()  : 0 ));
					postfix = new UnicodeOffsetLengthReader(reg.getCharacterReader(sloc.top(), charset.getValue()),  sloc.getOffset() + sloc.getLength(), -1);
				}
			}

			OutputStream outStream;
			
			if (prefix != null) {
				outStream = new ByteArrayOutputStream(FILE_BUFFER_SIZE);
			}
			else {
				outStream = reg.getOutputStream(sloc, append);
			}
			
			try (UnicodeOutputStreamWriter out = new UnicodeOutputStreamWriter(outStream, charset.getValue(), append)) {
				if (prefix != null) {
					copy(prefix, out);
				}
				for(IValue elem : V){
					if (elem.getType().isString()) {
					    ((IString) elem).write(out);
					}
					else if (elem.getType().isSubtypeOf(RascalValueFactory.Tree)) {
					  TreeAdapter.yield((IConstructor) elem, out);
					}
					else{
						out.append(elem.toString());
					}
				}
				if (postfix != null) {
					copy(postfix, out);
				}
			}
			
			if (prefix != null) {
				// we wrote to a buffer instead of the file
				try (OutputStream out = reg.getOutputStream(sloc, false)) {
					((ByteArrayOutputStream) outStream).writeTo(out);
				}
			}
		} 
		catch(FileNotFoundException fnfex){
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		} 
		catch (UnsupportedOperationException e) {
			assert false; // we tested for offset length above
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		} 
		catch (IOException ioex){
			throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()));
		}
		finally {
			try {
				if (prefix != null) {
					prefix.close();
				}
				if (postfix != null) {
					postfix.close();
				}
			} catch (IOException e) {
				throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
			}
		}
		
		return;
	}
	
	public void writeFileBytes(ISourceLocation sloc, IList blist){
		try (OutputStream out = URIResolverRegistry.getInstance().getOutputStream(sloc, false)) {
			Iterator<IValue> iter = blist.iterator();
			while (iter.hasNext()){
				IValue ival = iter.next();
				out.write((byte) (((IInteger) ival).intValue()));
			}
		}
		catch(FileNotFoundException e){
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		}
		catch(IOException e){
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
		return;
	}
	
	public void appendToFile(ISourceLocation sloc, IList V){
		writeFile(sloc, V, true);
	}
	
	public void appendToFileEnc(ISourceLocation sloc, IString charset, IList V){
		writeFileEnc(sloc, charset, V, true);
	}
	
	public IList readFileLines(ISourceLocation sloc){
		if(trackIO) System.err.println("readFileLines: " + sloc);
		try (Reader reader = URIResolverRegistry.getInstance().getCharacterReader(sloc)) {
			return consumeInputStreamLines(reader);
		}
		catch (MalformedURLException e) {
		    throw RuntimeExceptionFactory.malformedURI(sloc.toString());
		}
		catch (FileNotFoundException e) {
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		} 
	}
	
	public IList readFileLinesEnc(ISourceLocation sloc, IString charset){
		if(trackIO) System.err.println("readFileLinesEnc: " + sloc);
		try (Reader reader = URIResolverRegistry.getInstance().getCharacterReader(sloc,charset.getValue())) {
			return consumeInputStreamLines(reader);
		}
		catch (MalformedURLException e) {
		    throw RuntimeExceptionFactory.malformedURI(sloc.toString());
		}
		catch (FileNotFoundException e) {
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}

	private IList consumeInputStreamLines(Reader in) throws IOException {
		try (BufferedReader buf = new BufferedReader(in)) {
			String line = null;
			IListWriter res = values.listWriter();
			while ((line = buf.readLine()) != null) {
			    res.append(values.string(line));
			}
			return res.done();
		}
	}
	
	public IList readFileBytes(ISourceLocation sloc) {
		
		if(trackIO) System.err.println("readFileBytes: " + sloc);
		IListWriter w = values.listWriter();
		
		try (InputStream in = URIResolverRegistry.getInstance().getInputStream(sloc)) {
			byte bytes[] = new byte[FILE_BUFFER_SIZE];
			int read;

			while ((read = in.read(bytes, 0, bytes.length)) != -1) {
				for (int i = 0; i < read; i++) {
					w.append(values.integer(bytes[i] & 0xff));
				}
			} 
		}
		catch (FileNotFoundException e) {
			throw RuntimeExceptionFactory.pathNotFound(sloc);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}

		return w.done();
	}
	
    public IString uuencode(ISourceLocation sloc) {
        int BUFFER_SIZE = 3 * 512;
        Base64.Encoder encoder = Base64.getEncoder();
        
        try  (BufferedInputStream in = new BufferedInputStream(URIResolverRegistry.getInstance().getInputStream(sloc), BUFFER_SIZE); ) {
            StringBuilder result = new StringBuilder();
            byte[] chunk = new byte[BUFFER_SIZE];
            int len = 0;
            
            // read multiples of 3 until not possible anymore
            while ( (len = in.read(chunk)) == BUFFER_SIZE ) {
                 result.append( encoder.encodeToString(chunk) );
            }
            
            // read final chunk which is not a multiple of 3
            if ( len > 0 ) {
                 chunk = Arrays.copyOf(chunk,len);
                 result.append( encoder.encodeToString(chunk) );
            }
            
            return values.string(result.toString());
        }
        catch (IOException e) {
            throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
        }
    }
	
	public IString createLink(IString title, IString target) {
		return values.string("\uE007["+title.getValue().replaceAll("\\]", "_")+"]("+target.getValue()+")");
	}
	
	public ISourceLocation arbLoc() {
	    return (ISourceLocation) createRandomValue(TypeFactory.getInstance().sourceLocationType(), 1 + random.nextInt(5), 1 + random.nextInt(5));
	}
	
	/*
	 * List
	 */
	
	private WeakReference<IList> indexes;

	/**
	 * A mini class to wrap a lessThan function
	 */
	private class Less {
		private final IFunction less;

		Less(IFunction less) {
			this.less = less;
		}

		public boolean less(IValue x, IValue y) {
			return ((IBool) less.call(new IValue[] { x, y })).getValue();
		}
	}
	
	private class Sorting {
		private final IValue[] array;
		private final int size;
		private final Less less;

		private void swap(int i, int j) {
			IValue tmp = array[i];
			array[i] = array[j];
			array[j] = tmp;
		}

		public Sorting(IValue[] array, Less less) {
			this.array = array;
			this.size = array.length;
			this.less = less;
		}
 
		/**
		 * @throws IllegalArgument if comparator is illegal (i.e., if pivot equals pivot)
		 */
		public Sorting sort() {
			if (size == 0) {
				return this;
			}
			if (less.less(array[0], array[0])) {
				throw RuntimeExceptionFactory.illegalArgument(less.less,
					"Bad comparator: Did you use less-or-equals instead of less-than?");
			}
			sort(0, size - 1);

			return this;
		}

		public Sorting shuffle() {
			for (int i = 0; i < size; i++) {
				swap(i, i + (int) (Math.random() * (size - i)));
			}
			return this;
		}

		private void sort(int low, int high) {
			IValue pivot = array[low + (high - low) / 2];
			int oldLow = low;
			int oldHigh = high;

			while (low < high) {
				for (; less.less(array[low], pivot); low++);
				for (; less.less(pivot, array[high]); high--);

				if (low <= high) {
					swap(low, high);
					low++;
					high--;
				}
			}

			if (oldLow < high)
				sort(oldLow, high);
			if (low < oldHigh)
				sort(low, oldHigh);
		}
	}
	
	public IValue elementAt(IList lst, IInteger index) {
		if(lst.length() == 0)
			throw RuntimeExceptionFactory.emptyList();
		try {
			int i = index.intValue();
			if(index.intValue() < 0)
				i = i + lst.length();
			return lst.get(i);
		} catch (IndexOutOfBoundsException e){
			 throw RuntimeExceptionFactory.indexOutOfBounds(index);
		}
	}
	
	public IList shuffle(IList l, IInteger seed) {
		return l.shuffle(new Random(2305843009213693951L * seed.hashCode()));

	}

	public IList shuffle(IList l) {
		return l.shuffle(new Random());
	}
	
	public IList sort(IList l, IFunction cmpv){
		IValue[] tmpArr = new IValue[l.length()];
		for(int i = 0 ; i < l.length() ; i++){
			tmpArr[i] = l.get(i);
		}

		// we randomly swap some elements to make worst case complexity unlikely
		new Sorting(tmpArr, new Less(cmpv)).shuffle().sort();


		IListWriter writer = values.listWriter();
		writer.append(tmpArr);
		return writer.done();
	}
	
	public IList sort(ISet l, IFunction cmpv) {
		IValue[] tmpArr = new IValue[l.size()];
		int i = 0;
		
		// we assume that the set is reasonably randomly ordered, such
		// that the worst case of quicksort is unlikely
		for (IValue elem : l){
			tmpArr[i++] = elem;
		}
		
		new Sorting(tmpArr, new Less(cmpv)).sort();
		
		IListWriter writer = values.listWriter();
		for(IValue v : tmpArr){
			writer.append(v);
		}
		
		return writer.done();
	}
	
	public IList top(IInteger k, ISet l, IFunction cmpv) {
        final LinkedList<IValue> result = new LinkedList<>();
        final Less less = new Less(cmpv);
        final int K = k.intValue();
        final int absK = Math.abs(K);
        
        if (K == 0) {
            return values.list();
        }
        
        for (IValue n : l) {
            if (result.isEmpty()) {
                result.add(n);
            } else {
                int i = 0;
                
                for (IValue m : result) {
                    if (K > 0 ? less.less(n, m) : less.less(m, n))  {
                        result.add(i, n);

                        if (result.size() > absK) {
                            result.remove(absK);
                        }
                        break;
                    }

                    i++;
                }
            }
        }
        
        IListWriter w = values.listWriter();
        w.appendAll(result);
        return w.done();
    }
	
	private IList makeUpTill(int from,int len){
		IListWriter writer = values.listWriter();
		for(int i = from ; i < len; i++){
			writer.append(values.integer(i));
		}
		return writer.done();
	}
	
	public IValue delete(IList lst, IInteger n)
	// @doc{delete -- delete nth element from list}
	{
		try {
			return lst.delete(n.intValue());
		} catch (IndexOutOfBoundsException e){
			 throw RuntimeExceptionFactory.indexOutOfBounds(n);
		}
	}
	
	public IValue domain(IList lst)
	//@doc{domain -- a list of all legal index values for a list}
	{
		ISetWriter w = values.setWriter();
		int len = lst.length();
		for (int i = 0; i < len; i++){
			w.insert(values.integer(i));
		}
		return w.done();
	}
	
	public IValue head(IList lst)
	// @doc{head -- get the first element of a list}
	{
	   if(lst.length() > 0){
	      return lst.get(0);
	   }
	   
	   throw RuntimeExceptionFactory.emptyList();
	}

	public IValue head(IList lst, IInteger n)
	  throws IndexOutOfBoundsException
	// @doc{head -- get the first n elements of a list}
	{
	   try {
	      return lst.sublist(0, n.intValue());
	   } catch(IndexOutOfBoundsException e){
		   IInteger end = values.integer(n.intValue() - 1);
	      throw RuntimeExceptionFactory.indexOutOfBounds(end);
	   }
	}

	public IValue getOneFrom(IList lst)
	//@doc{getOneFrom -- get an arbitrary element from a list}
	{
		int n = lst.length();
		if(n > 0){
			return lst.get(random.nextInt(n));
		}
		
		throw RuntimeExceptionFactory.emptyList();
	}

	public IValue insertAt(IList lst, IInteger n, IValue elm)
	  throws IndexOutOfBoundsException
	 //@doc{insertAt -- add an element at a specific position in a list}
	 {
	 	IListWriter w = values.listWriter();
	 	
	 	int k = n.intValue();
	    if(k >= 0 && k <= lst.length()){
	      if(k == lst.length()){
	      	w.insert(elm);
	      }
	      for(int i = lst.length()-1; i >= 0; i--) {
	        w.insert(lst.get(i));
	        if(i == k){
	        	w.insert(elm);
	        }
	      }
	      return w.done();
	    }
	    
		throw RuntimeExceptionFactory.indexOutOfBounds(n);
	 }
	
	public IValue isEmpty(IList lst)
	//@doc{isEmpty -- is list empty?}
	{
		return values.bool(lst.length() == 0);
	}

	public IValue reverse(IList lst)
	//@doc{reverse -- elements of a list in reverse order}
	{
		return lst.reverse();
	}

	public IValue size(IList lst)
	//@doc{size -- number of elements in a list}
	{
	   return values.integer(lst.length());
	}

	 public IValue slice(IList lst, IInteger start, IInteger len)
	 //@doc{slice -- sublist from start of length len}
	 {
		try {
			return lst.sublist(start.intValue(), len.intValue());
		} catch (IndexOutOfBoundsException e){
			IInteger end = values.integer(start.intValue() + len.intValue());
			throw RuntimeExceptionFactory.indexOutOfBounds(end);
		}
	 }

	 public IValue tail(IList lst)
	 //@doc{tail -- all but the first element of a list}
	 {
	 	try {
	 		return lst.sublist(1, lst.length()-1);
	 	} catch (IndexOutOfBoundsException e){
	 		throw RuntimeExceptionFactory.emptyList();
	 	}
	 }
	 
	  public IValue tail(IList lst, IInteger len)
	 //@doc{tail -- last n elements of a list}
	 {
	 	int lenVal = len.intValue();
	 	int lstLen = lst.length();
	 
	 	try {
	 		return lst.sublist(lstLen - lenVal, lenVal);
	 	} catch (IndexOutOfBoundsException e){
	 		IInteger end = values.integer(lenVal - lstLen);
	 		throw RuntimeExceptionFactory.indexOutOfBounds(end);
	 	}
	 }
	  
	public IValue take(IInteger len, IList lst) {
	   //@doc{take -- take n elements of from front of a list}
		int lenVal = len.intValue();
		int lstLen = lst.length();
		if(lenVal >= lstLen){
			return lst;
		} else {
			return lst.sublist(0, lenVal);
		}
	}

	public IValue drop(IInteger len, IList lst) {
	   //@doc{drop -- remove n elements of from front of a list}
		int lenVal = len.intValue();
		int lstLen = lst.length();
		if(lenVal >= lstLen){
			return values.list();
		} else {
			return lst.sublist(lenVal, lstLen - lenVal);
		}
	}
	
	public IValue upTill(IInteger ni) {
		//@doc{Returns the list 0..n, this is slightly faster than [0,1..n], since the returned values are shared}
		int n = ni.intValue();
		if(indexes == null || indexes.get() == null) {
			IList l = makeUpTill(0, n);
			indexes = new WeakReference<IList>(l);
			return indexes.get();
		} else {
			IList l = indexes.get(); // strong ref
			if(l == null || n >= l.length()){ 
				l = makeUpTill(0,n);
				indexes =  new WeakReference<IList>(l);
				return l;
			}
			return l.sublist(0, n);
		} 
	}
	
	public IValue prefix(IList lst) {
		   //@doc{Return all but the last element of a list}
			int lstLen = lst.length();
			if(lstLen <= 1){
				return values.list();
			} else {
				return lst.sublist(0, lstLen - 1);
			}
		}


	 
	public IValue takeOneFrom(IList lst)
	//@doc{takeOneFrom -- remove an arbitrary element from a list, returns the element and the modified list}
	{
	   int n = lst.length();
	   
	   if(n > 0){
	   	  int k = random.nextInt(n);
	   	  IValue pick = lst.get(0);
	   	  IListWriter w = values.listWriter();
	  
	      for(int i = n - 1; i >= 0; i--) {
	         if(i == k){
	         	pick = lst.get(i);
	         } else {
	            w.insert(lst.get(i));
	         }
	      }
	      return values.tuple(pick, w.done());
	   	}
	   
	   throw RuntimeExceptionFactory.emptyList();
	}
	
	public IMap toMap(IList lst)
	// @doc{toMap -- convert a list of tuples to a map; first value in old tuples is associated with a set of second values}
	{ 	    
	    Map<IValue,IListWriter> hm = new HashMap<>();

        for (IValue v : lst) {
            ITuple t = (ITuple) v;
            IValue key = t.get(0);
            IValue val = t.get(1);
            IListWriter wValList = hm.get(key);
            if(wValList == null){
                wValList = values.listWriter();
                hm.put(key, wValList);
            }
            wValList.append(val);
        }
		
		IMapWriter w = values.mapWriter();
		for(IValue v : hm.keySet()){
			w.put(v, hm.get(v).done());
		}
		return w.done();
	}
	
	public IValue toMapUnique(IList lst)
	//@doc{toMapUnique -- convert a list of tuples to a map; result should be a map}
	{
	   if(lst.length() == 0){
	      return values.mapWriter().done();
	   }
	  
	   IMapWriter w = values.mapWriter();
	   Map<IValue,IValue> seenKeys = new HashMap<>();
	   for(IValue v : lst){
		   ITuple t = (ITuple) v;
		   IValue key = t.get(0);
		   IValue val = t.get(1);
		   if(seenKeys.containsKey(key)) { 
		       throw RuntimeExceptionFactory.MultipleKey(key, seenKeys.get(key), val);
		   }
		   seenKeys.put(key, val);
		   w.put(key, val);
	   }
	   return w.done();
	}

	public IValue toSet(IList lst)
	//@doc{toSet -- convert a list to a set}
	{
	  ISetWriter w = values.setWriter();
	  
	  for(IValue v : lst){
	    w.insert(v);
	  }
		
	  return w.done();
	}

	public IValue toString(IList lst)
	//@doc{toString -- convert a list to a string}
	{
		return values.string(lst.toString());
	}

	public IValue itoString(IList lst)
	//@doc{toString -- convert a list to a string}
	{
		return itoStringValue(lst);
	}

	private IValue itoStringValue(IValue T)
	//@doc{toString -- convert a node to a string}
	{
		StandardTextWriter w = new StandardTextWriter(true, 2);
		StringWriter result = new StringWriter();
		try {
			w.write(T, result);
			return values.string(result.toString());
		} 
		catch (IOException e) {
			RuntimeExceptionFactory.io(values.string("Could not convert list to indented value"));
			throw new RuntimeException("previous command should always throw");
		}
	}
	
	/*
	 * Map
	 */
	
	public IValue delete(IMap M, IValue key) {
	    return M.removeKey(key);
	}
	
	public IValue domain(IMap M)
	//@doc{domain -- return the domain (keys) of a map}

	{
	  ISetWriter w = values.setWriter();
	  Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
	  while (iter.hasNext()) {
	    Entry<IValue,IValue> entry = iter.next();
	    w.insert(entry.getKey());
	  }
	  return w.done();
	}

	public IValue getOneFrom(IMap m)  
	//@doc{getOneFrom -- return arbitrary key of a map}
	{
	   int i = 0;
	   int sz = m.size();
	   if(sz == 0){
	      throw RuntimeExceptionFactory.emptyMap();
	   }
	   int k = random.nextInt(sz);
	   Iterator<Entry<IValue,IValue>> iter = m.entryIterator();
	  
	   while(iter.hasNext()){
	      if(i == k){
	      	return (iter.next()).getKey();
	      }
	      iter.next();
	      i++;
	   }
	   
	   throw RuntimeExceptionFactory.emptyMap();
	}
	
	public IValue invertUnique(IMap M)
	//@doc{invertUnique -- return map with key and value inverted; values are unique}
	{
		IMapWriter w = values.mapWriter();
		Map<IValue,IValue> seenValues = new HashMap<>();
		Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
		while (iter.hasNext()) {
			Entry<IValue,IValue> entry = iter.next();
			IValue key = entry.getKey();
			IValue val = entry.getValue();
			if (seenValues.containsKey(val)) {
					throw RuntimeExceptionFactory.MultipleKey(val, key, seenValues.get(val));
			}
			seenValues.put(val, key);
			w.put(val, key);
		}
		return w.done();
	}
	
	public IValue invert(IMap M)
	//@doc{invert -- return map with key and value inverted; values are not unique and are collected in a set}
	{
		Map<IValue,ISetWriter> hm = new HashMap<>();
		Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
		while (iter.hasNext()) {
			Entry<IValue,IValue> entry = iter.next();
			IValue key = entry.getKey();
			IValue val = entry.getValue();
			hm.computeIfAbsent(val, (k) -> values.setWriter()).insert(key);
		}
		
		IMapWriter w = values.mapWriter();
		for(Entry<IValue, ISetWriter> v : hm.entrySet()){
			w.put(v.getKey(), v.getValue().done());
		}
		return w.done();
	}
	
	public IValue isEmpty(IMap M)
	//@doc{isEmpty -- is map empty?}
	{
		return values.bool(M.size() == 0);
	}

	public IValue range(IMap M)
	//@doc{range -- return the range (values) of a map}
	{
	  ISetWriter w = values.setWriter();
	  Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
	  while (iter.hasNext()) {
	    Entry<IValue,IValue> entry = iter.next();
	    w.insert(entry.getValue());
	  }
	  return w.done();
	}

	public IValue size(IMap M)
	{
		return values.integer(M.size());
	}

	public IValue toList(IMap M)
	//@doc{toList -- convert a map to a list}
	{
	  IListWriter w = values.listWriter();
	  Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
	  while (iter.hasNext()) {
	    Entry<IValue,IValue> entry = iter.next();
	    w.insert(values.tuple(entry.getKey(), entry.getValue()));
	  }
	  return w.done();
	}

	public IValue toRel(IMap M) {
	    //@doc{toRel -- convert a map to a relation}
	    ISetWriter w = values.setWriter();
	    Iterator<Entry<IValue,IValue>> iter = M.entryIterator();
	    while (iter.hasNext()) {
	        Entry<IValue,IValue> entry = iter.next();
	        w.insert(values.tuple(entry.getKey(), entry.getValue()));
	    }
	    return w.done();
	}
	  
	public IValue toString(IMap M)
	{
	  return values.string(M.toString());
	}

	public IValue itoString(IMap M)
	{
		return itoStringValue(M);
	}
	/*
	 * Node
	 */

	public IValue arity(INode T)
	//@doc{arity -- number of children of a node}
	{
	   return values.integer(T.arity());
	}

	public IValue getChildren(INode T)
	//@doc{getChildren -- get the children of a node}
	{
		IListWriter w = values.listWriter();
		
		for(IValue v : T.getChildren()){
			w.append(v);
		}
		return w.done();
	}
	
	public IValue getKeywordParameters(INode T)
	//@doc{getChildren -- get the children of a node}
	{
		IMapWriter w = values.mapWriter();
		
		if (T.mayHaveKeywordParameters()) {
			for(Entry<String, IValue> e : T.asWithKeywordParameters().getParameters().entrySet()){
				w.put(values.string(e.getKey()), e.getValue());
			}
		}
		
		return w.done();
	}

	public IValue getName(INode T)
	//@doc{getName -- get the function name of a node}
	{
		return values.string(T.getName());
	}

	public IValue makeNode(IString N, IList V, IMap kwParams)
	//@doc{makeNode -- create a node given its function name and arguments}
	{
	    IList argList = V;
		IValue args[] = new IValue[argList.length()];
		int i = 0;
		for(IValue v : argList){
			args[i++] = v;
		}
		
		Map<String,IValue> map = new HashMap<>();
		for (IValue key : kwParams) {
			map.put(((IString) key).getValue(), kwParams.get(key));
		}
		
		return values.node(N.getValue(), args, map);
	}
	
	public IValue toString(INode T)
	//@doc{toString -- convert a node to a string}
	{
		return values.string(T.toString());

	}

	public IValue itoString(INode T)
	//@doc{toString -- convert a node to a string}
	{
		return itoStringValue(T);
	}
	
	public INode setKeywordParameters(INode node, IMap kwargs) {
		Map<String,IValue> map = new HashMap<java.lang.String,IValue>();
		kwargs.entryIterator().forEachRemaining((kv) -> map.put(((IString)kv.getKey()).getValue(), kv.getValue()));
		return node.asWithKeywordParameters().setParameters(map);
	}
	
	public INode unset(INode node, IString label) {
        return node.mayHaveKeywordParameters() ? node.asWithKeywordParameters().unsetParameter(label.getValue()) : node;
    }
    
    public INode unset(INode node) {
        return  node.mayHaveKeywordParameters() ? node.asWithKeywordParameters().unsetAll() : node;
    }
    
    public INode arbNode() {
        return (INode) createRandomValue(TypeFactory.getInstance().nodeType(), 1 + random.nextInt(5), 1 + random.nextInt(5));
    }
	
	/*
	 * ParseTree
	 */
	
	protected final TypeReifier tr;

	public IFunction parser(IValue start,  IBool allowAmbiguity, IBool hasSideEffects, IBool firstAmbiguity, ISet filters) {
	    return rascalValues.parser(start, allowAmbiguity, hasSideEffects, firstAmbiguity, filters);
	}
	
	public IFunction parsers(IValue start,  IBool allowAmbiguity, IBool hasSideEffects, IBool firstAmbiguity, ISet filters) {
        return rascalValues.parsers(start, allowAmbiguity, hasSideEffects, firstAmbiguity, filters);
    }
	
	// REFLECT -- copy in {@link PreludeCompiled}
	protected IConstructor makeConstructor(TypeStore store, Type returnType, String name, IValue ...args) {
	    IValue value = values.constructor(store.lookupConstructor(returnType, name, TypeFactory.getInstance().tupleType(args)), args, new HashMap<String, IValue>());
        Type type = value.getType();
        if (type.isAbstractData()) {
            return (IConstructor)value;
        }
        throw RuntimeExceptionFactory.implodeError("Calling of constructor " + name + " did not return a constructor");
	}
	
	protected java.lang.String unescapedConsName(ITree tree) {
		java.lang.String x = TreeAdapter.getConstructorName(tree);
		if (x != null) {
			x = x.replaceAll("\\\\", "");
		}
		return x;
	}

	protected Set<Type> findConstructors(Type type, java.lang.String constructorName, int arity,  TypeStore store) {
		Set<Type> constructors = new HashSet<Type>();
		
		for (Type constructor : store.lookupConstructor(type, constructorName)) {
			if (constructor.getArity() == arity)
				constructors.add(constructor);
		}
		
		return constructors;
	}

	// REFLECT -- copy in {@link PreludeCompiled}
	public IValue implode(IValue reifiedType, IConstructor arg) {
		ITree tree = (ITree) arg;
		
		TypeStore store = new TypeStore();
		Type type = tr.valueToType((IConstructor) reifiedType, store);
		try {
			IValue result = implode(store, type, tree, false); 
			if (isUntypedNodeType(type) && !type.isTop() && (TreeAdapter.isList(tree) || TreeAdapter.isOpt(tree))) {
				// Ensure the result is actually a node, even though
				// the tree given to implode is a list.
				result = values.node("", result);
			}
			return result;
		}
		catch (Backtrack b) {
			throw b.exception;
		}
	}

	@SuppressWarnings("serial")
	protected static class Backtrack extends RuntimeException {
		Throw exception;
		public Backtrack(Throw exception) {
			this.exception = exception;
		}
		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}
	
	private IValue[] implodeArgs(TypeStore store, Type type, IList args) {
		int length = args.length();
		IValue implodedArgs[] = new IValue[length];
		for (int i = 0; i < length; i++) {
			Type argType = isUntypedNodeType(type) ? type : type.getFieldType(i);
			implodedArgs[i] = implode(store, argType, (ITree)args.get(i), false);
		}
		return implodedArgs;
	}
	
	
	protected IValue implode(TypeStore store, Type type, IConstructor arg0, boolean splicing) {
		ITree tree = (ITree) arg0;
		Backtrack failReason = null;
		
		// always yield if expected type is str, except if regular 
		if (type.isString() && !splicing) {
			return values.string(TreeAdapter.yield(tree));
		}

		if (SymbolAdapter.isStartSort(TreeAdapter.getType(tree))) {
			IList args = TreeAdapter.getArgs(tree);
			ITree before = (ITree) args.get(0);
			ITree ast = (ITree) args.get(1);
			ITree after = (ITree) args.get(2);
			IValue result = implode(store, type, ast, splicing);
			if (result.getType().isNode()) {
				IMapWriter comments = values.mapWriter();
				comments.putAll((IMap)((INode)result).asWithKeywordParameters().getParameter("comments"));
				IList beforeComments = extractComments(before);
				if (!beforeComments.isEmpty()) {
					comments.put(values.integer(-1), beforeComments);
				}
				IList afterComments = extractComments(after);
				if (!afterComments.isEmpty()) {
					comments.put(values.integer(((INode)result).arity()), afterComments);
				}
				result = ((INode)result).asWithKeywordParameters().setParameter("comments", comments.done());
			}
			return result;
		}
		
		if (TreeAdapter.isLexical(tree)) {
			java.lang.String constructorName = unescapedConsName(tree);
			java.lang.String yield = TreeAdapter.yield(tree);
			if (constructorName != null) {
				// make a single argument constructor  with yield as argument
				// if there is a singleton constructor with a str argument
				if (!type.isAbstractData() && !isUntypedNodeType(type)) {
					throw RuntimeExceptionFactory.illegalArgument(tree, "Constructor (" + constructorName + ") should match with abstract data type and not with " + type);
				}
				
				if (isUntypedNodeType(type)) {
					return values.node(constructorName, values.string(yield));
				}
				
				Set<Type> conses = findConstructors(type, constructorName, 1, store);
				Iterator<Type> iter = conses.iterator();
				while (iter.hasNext()) {
					try {
						@SuppressWarnings("unused")
						Type cons = iter.next();
						ISourceLocation loc = TreeAdapter.getLocation(tree);
						IConstructor ast = makeConstructor(store, type, constructorName, values.string(yield));
						return ast.asWithKeywordParameters().setParameter("location", loc);
					}
					catch (Backtrack b) {
					    failReason = b;
						continue;
					}
				}
				
				throw failReason != null ? failReason : new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, "Cannot find a constructor " + type));
			}
			if (type.isInteger()) {
				return values.integer(yield);
			}
			if (type.isReal()) {
				return values.real(yield);
			}
			if (type.isBool()) {
				if (yield.equals("true")) {
					return values.bool(true);
				}
				if (yield.equals("false")) {
					return values.bool(false);
				}
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, "Bool type does not match with " + yield));
			}
			if (type.isString() || isUntypedNodeType(type)) {
				// NB: in "node space" all lexicals become strings
				return values.string(yield);
			}
			
			throw RuntimeExceptionFactory.illegalArgument(tree, "Missing lexical constructor");
		}
		
		//Set implementation added here by Jurgen at 19/07/12 16:45
		if (TreeAdapter.isList(tree)) {
			if (type.isList() || splicing || isUntypedNodeType(type)) {
				// if in node space, we also make a list; 
				// NB: this breaks type safety if the top-level tree
				// is itself a list.
				
				Type elementType = type;
				if (!splicing && !isUntypedNodeType(type)) {
					elementType = type.getElementType();
				}
				IListWriter w = values.listWriter();
				for (IValue arg: TreeAdapter.getListASTArgs(tree)) {
					w.append(implode(store, elementType, (ITree) arg, false));
				}
				return w.done();
			}
			else if (type.isSet()) {
				Type elementType = splicing ? type : type.getElementType();
				ISetWriter w = values.setWriter();
				for (IValue arg: TreeAdapter.getListASTArgs(tree)) {
					w.insert(implode(store, elementType, (ITree) arg, false));
				}
				return w.done();
			}
			else {
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, "Cannot match list with " + type));
			}
		}
		//Changes end here
		
		if (TreeAdapter.isOpt(tree) && type.isBool()) {
			IList args = TreeAdapter.getArgs(tree);
			if (args.isEmpty()) {
				return values.bool(false);
			}
			return values.bool(true);
		}
		
		if (TreeAdapter.isOpt(tree)) {
			if (!type.isList() && !isUntypedNodeType(type)) {
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, "Optional should match with a list and not " + type));
			}
			Type elementType = isUntypedNodeType(type) ? type : type.getElementType();
			IListWriter w = values.listWriter();
			for (IValue arg: TreeAdapter.getASTArgs(tree)) {
				IValue implodedArg = implode(store, elementType, (ITree) arg, true);
				if (implodedArg instanceof IList) {
					// splicing
					for (IValue nextArg: (IList)implodedArg) {
						w.append(nextArg);
					}
				}
				else {
					w.append(implodedArg);
				}
				// opts should have one argument (if any at all)
				break;
			}
			return w.done();
		}
		
		if (TreeAdapter.isAmb(tree)) {
			if (!type.isSet()) {
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, "Ambiguous node should match with set and not " + type));
			}
			Type elementType = type.getElementType();
			ISetWriter w = values.setWriter();
			for (IValue arg: TreeAdapter.getAlternatives(tree)) {
				w.insert(implode(store, elementType, (ITree) arg, false));
			}
			return w.done();
		}
		
		if (ProductionAdapter.hasAttribute(TreeAdapter.getProduction(tree), RascalValueFactory.Attribute_Bracket)) {
			return implode(store, type, (ITree) TreeAdapter.getASTArgs(tree).get(0), false);
		}
		
		if (TreeAdapter.isAppl(tree)) {
			IList args = TreeAdapter.getASTArgs(tree);
			
			int j = 0;
			IMapWriter cw = values.mapWriter();
			IListWriter aw = values.listWriter();
			for (IValue kid : TreeAdapter.getArgs(tree)) {
				if (TreeAdapter.isLayout((ITree) kid)) {
					IList cts = extractComments((ITree) kid);
					if (!cts.isEmpty()) {
					  cw.put(values.integer(j), cts);
					}
					j++;
				}
				else if (!TreeAdapter.isLiteral((ITree) kid) && 
						!TreeAdapter.isCILiteral((ITree) kid) && 
						!TreeAdapter.isEmpty((ITree) kid)) {
					aw.append(kid);
				}
			}
			args = aw.done();
			int length = args.length();
			IMap comments = cw.done();
			
//			// this could be optimized.
//			i = 0;
//			int length = args.length();
//			while (i < length) {
//				if (TreeAdapter.isEmpty((IConstructor) args.get(i))) {
//					length--;
//					args = args.delete(i);
//				}
//				else {
//					i++;
//				}
//			}
			
			
			java.lang.String constructorName = unescapedConsName(tree);			
			
			if (constructorName == null) {
				if (length == 1) {
					// jump over injection
					return implode(store, type, (ITree) args.get(0), splicing);
				}
				
				
				// make a tuple if we're in node space
				if (isUntypedNodeType(type)) {
					return values.tuple(implodeArgs(store, type, args));
				}

				if (!type.isTuple()) {
					throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, "Constructor does not match with " + type));
				}
				
				if (length != type.getArity()) {
					throw new Backtrack(RuntimeExceptionFactory.arityMismatch(type.getArity(), length));
				}

				return values.tuple(implodeArgs(store, type, args));
			}
			
			// if in node space, make untyped nodes
			if (isUntypedNodeType(type)) {
				INode ast = values.node(constructorName, implodeArgs(store, type, args));
				return ast.asWithKeywordParameters().setParameter("location", TreeAdapter.getLocation(tree)).asWithKeywordParameters().setParameter("comments", comments);
			}
			
			// make a typed constructor
			if (!type.isAbstractData()) {
				throw new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, "Constructor (" + constructorName + ") should match with abstract data type and not with " + type));
			}

			Set<Type> conses = findConstructors(type, constructorName, length, store);
			Iterator<Type> iter = conses.iterator();
			
			
			while (iter.hasNext()) {
				try {
					Type cons = iter.next();
					ISourceLocation loc = TreeAdapter.getLocation(tree);
					IValue[] implodedArgs = implodeArgs(store, cons, args);
					IConstructor ast = makeConstructor(store, type, constructorName, implodedArgs);
					return ast
					        .asWithKeywordParameters()
					        .setParameter("location", loc)
					        .asWithKeywordParameters()
					        .setParameter("comments", comments);
				}
				catch (Backtrack b) {
				    failReason = b;
					continue;
				}
			}
			
			throw failReason != null ? failReason : new Backtrack(RuntimeExceptionFactory.illegalArgument(tree,
                "Cannot find a constructor for " + type + " with name " + constructorName + " and arity " + length + " for syntax type \'" + ProductionAdapter.getSortName(TreeAdapter.getProduction(tree)) + "\'"));
		}
		
		throw failReason != null ? failReason : new Backtrack(RuntimeExceptionFactory.illegalArgument(tree, 
				"Cannot find a constructor for " + type));
	}
	
	private IList extractComments(IConstructor layout) {
		final IListWriter comments = values.listWriter();
		TreeVisitor<RuntimeException> visitor = new TreeVisitor<RuntimeException>() {

			@Override
			public ITree visitTreeAppl(ITree arg)
					 {
				if (TreeAdapter.isComment(arg)) {
					comments.append(values.string(TreeAdapter.yield(arg)));
				}
				else {
					for (IValue t: TreeAdapter.getArgs(arg)) {
						t.accept(this);
					}
				}
				return arg;
			}

			@Override
			public ITree visitTreeAmb(ITree arg)
					 {
				return arg;
			}

			@Override
			public ITree visitTreeChar(ITree arg)
					 {
				return arg;
			}

			@Override
			public ITree visitTreeCycle(ITree arg)
					 {
				return arg;
			}
			
		};
		
		layout.accept(visitor);
		return comments.done();
	}

	protected boolean isUntypedNodeType(Type type) {
		return (type.isNode() && !type.isConstructor() && !type.isAbstractData()) 
				|| type.isTop();
	}
	
	/*
	 * Rational
	 */

	public IValue numerator(IRational n)
	{
		return n.numerator();
	}

	public IValue denominator(IRational n)
	{
	  return n.denominator();
	}

	public IValue remainder(IRational n)
	{
	  return n.remainder();
	}
	
	/*
	 * Relation
	 */
	
	/*
	 * Set
	 */
	public IValue getOneFrom(ISet st)
	// @doc{getOneFrom -- pick a random element from a set}
	{
		int sz = st.size();

		if (sz == 0) {
			throw RuntimeExceptionFactory.emptySet();
		}
		int k = random.nextInt(sz);
		int i = 0;

		for (IValue v : st) {
			if (i == k) {
				return v;
			}
			i++;
		}
		
		throw RuntimeExceptionFactory.emptySet();
	}

	public IValue isEmpty(ISet st)
	//@doc{isEmpty -- is set empty?}
	{
		return values.bool(st.size() == 0);
	}
	
	public IValue size(ISet st)
	// @doc{size -- number of elements in a set}
	{
		return values.integer(st.size());
	}
	
	public IMap index(ISet s) {
	    return indexIterable(values, s, s.size());
	}
	public IMap index(IList l) {
	    return indexIterable(values, l, l.length());
	}
	
    public static IMap index(IValueFactory vf, ISet s) {
	    return indexIterable(vf, s, s.size());
	}
	
	private static IMap indexIterable(IValueFactory values, Iterable<IValue> s, int suggestedSize) {
		Map<IValue, ISetWriter> map = new HashMap<>(suggestedSize);
		
		for (IValue t : s) {
			ITuple tuple = (ITuple) t;
			IValue key = tuple.get(0);
			IValue value = tuple.get(1);
			map.computeIfAbsent(key, (k) -> values.setWriter()).insert(value);
		}
		
		IMapWriter mapWriter = values.mapWriter();
		for (Entry<IValue, ISetWriter> ent: map.entrySet()) {
			mapWriter.put(ent.getKey(), ent.getValue().done());
		}
		return mapWriter.done();
	}
	

	public IValue takeOneFrom(ISet st)
	// @doc{takeOneFrom -- remove an arbitrary element from a set,
	//      returns the element and the modified set}
	{
		int n = st.size();

		if (n > 0) {
			int i = 0;
			int k = random.nextInt(n);
			IValue pick = null;
			ISetWriter w = values.setWriter();

			for (IValue v : st) {
				if (i == k) {
					pick = v;
				} else {
					w.insert(v);
				}
				i++;
			}
			return values.tuple(pick, w.done());
		}
		throw RuntimeExceptionFactory.emptySet();
	}

	public IValue toList(ISet st)
	// @doc{toList -- convert a set to a list}
	{
		IListWriter w = values.listWriter();

		for (IValue v : st) {
			w.insert(v);
		}

		return w.done();
	}

	public IValue toMap(ISet st)
	// @doc{toMap -- convert a set of tuples to a map; value in old map is associated with a set of keys in old map}
	{
		Map<IValue,ISetWriter> hm = new HashMap<>();

		for (IValue v : st) {
			ITuple t = (ITuple) v;
			IValue key = t.get(0);
			IValue val = t.get(1);
			ISetWriter wValSet = hm.get(key);
			if(wValSet == null){
				wValSet = values.setWriter();
				hm.put(key, wValSet);
			}
			wValSet.insert(val);
		}
		
		IMapWriter w = values.mapWriter();
		for(IValue v : hm.keySet()){
			w.put(v, hm.get(v).done());
		}
		return w.done();
	}
	
	public IValue toMapUnique(ISet st)
	// @doc{toMapUnique -- convert a set of tuples to a map; keys are unique}
	{
		IMapWriter w = values.mapWriter();
		HashMap<IValue, IValue> seenKeys = new HashMap<>();

		for (IValue v : st) {
			ITuple t = (ITuple) v;
			IValue key = t.get(0);
			IValue val = t.get(1); 
			if(seenKeys.containsKey(key)) {  
				throw RuntimeExceptionFactory.MultipleKey(key, seenKeys.get(key), val);
			}
			seenKeys.put(key, val);
			w.put(key, val);
		}
		return w.done();
	}

	public IValue toString(ISet st)
	// @doc{toString -- convert a set to a string}
	{
		return values.string(st.toString());
	}

	public IValue itoString(ISet st)
	{
		return itoStringValue(st);
	}
	
	/*
	 * String
	 */
	
	public IString arbString(IInteger n) {
	    return (IString) createRandomValue(TypeFactory.getInstance().stringType(), n.intValue(), n.intValue());
	}

	
	public IBool isValidCharacter(IInteger i) {
		return values.bool(Character.isValidCodePoint(i.intValue()));
	}
	
	public IValue stringChar(IInteger i) {
		int intValue = i.intValue();
		if (Character.isValidCodePoint(intValue)) {
			return values.string(intValue);
		}
		else {
			throw RuntimeExceptionFactory.illegalArgument(i);
		}
	}
	
	public IValue stringChars(IList lst){
		int[] chars = new int[lst.length()];
		
		for (int i = 0; i < lst.length(); i ++) {
			chars[i] = ((IInteger) lst.get(i)).intValue();
			if (!Character.isValidCodePoint(chars[i])) {
				throw RuntimeExceptionFactory.illegalArgument(values.integer(chars[i]));
			}
		}
		
		return values.string(chars);
	}
	
	public IValue charAt(IString s, IInteger i) throws IndexOutOfBoundsException
	//@doc{charAt -- return the character at position i in string s.}
	{
	  try {
		return values.integer(s.charAt(i.intValue()));
	  }
	  catch (IndexOutOfBoundsException e) {
	    throw RuntimeExceptionFactory.indexOutOfBounds(i);
	  }
	}

	public IValue endsWith(IString s, IString suffix)
	//@doc{endWith -- returns true if string s ends with given string suffix.}
	{
	  return values.bool(s.getValue().endsWith(suffix.getValue()));
	}
	
	public IString trim(IString s) {
		return values.string(s.getValue().trim());
	}
	
	public IString squeeze(IString src, IString charSet) {
		//@{http://commons.apache.org/lang/api-2.6/index.html?org/apache/commons/lang/text/package-summary.html}
		String s = CharSetUtils.squeeze(src.getValue(), charSet.getValue());
		return values.string(s);
	}
	
	public IString capitalize(IString src) {
		StringBuilder result = new StringBuilder(src.length());
		boolean lastWhitespace= true;
		for (int cIndex =0; cIndex < src.length(); cIndex ++) {
			int cp = src.charAt(cIndex);
			if (Character.isWhitespace(cp)) {
				lastWhitespace = true;
			}
			else if (lastWhitespace) {
				lastWhitespace = false;
				cp = Character.toUpperCase(cp);
			}
			result.appendCodePoint(cp);
		}
		return values.string(result.toString());
	}
	
	public IString uncapitalize(IString src) {
		StringBuilder result = new StringBuilder(src.length());
		boolean lastWhitespace= true;
		for (int cIndex =0; cIndex < src.length(); cIndex ++) {
			int cp = src.charAt(cIndex);
			if (Character.isWhitespace(cp)) {
				lastWhitespace = true;
			}
			else if (lastWhitespace) {
				lastWhitespace = false;
				cp = Character.toLowerCase(cp);
			}
			result.appendCodePoint(cp);
		}
		return values.string(result.toString());
	}
	
	public IList split(IString sep, IString src) {
		String[] lst = src.getValue().split(Pattern.quote(sep.getValue()));
		IListWriter lw = values.listWriter();
		for (String s: lst) {
			lw.append(values.string(s));
		}
		return lw.done();
	}
	
	public IString wrap(IString src, IInteger wrapLength) {
		int wrapAt = wrapLength.intValue();
		if (wrapAt < 1) {
			wrapAt = 1;
		}
		final int iLength = src.length(); 

		final StringBuilder result = new StringBuilder(iLength + (iLength / wrapAt));
		
		int lineBegin = 0;
		while (iLength - lineBegin > wrapAt) {
			while (lineBegin < iLength && src.charAt(lineBegin) == ' ') {
				// skip over leading spaces
				lineBegin++;
			}
			// find wrapping point closest to border
			int lineEnd = lineBegin + wrapAt;
			while (lineEnd > lineBegin && lineEnd < iLength && src.charAt(lineEnd) != ' ') {
				lineEnd--;
			}
			if (lineEnd > lineBegin) {
				// we found a wrap point
				result.append(src.substring(lineBegin, lineEnd).getValue());
				result.append(System.lineSeparator());
				lineBegin = lineEnd + 1;
			}
			else {
				// long word, not breakable, lets search for the end
				lineEnd = lineBegin + wrapAt;
				while (lineEnd < iLength && src.charAt(lineEnd) != ' ') {
					lineEnd++;
				}
				result.append(src.substring(lineBegin, lineEnd).getValue());
				if (lineEnd < iLength) {
					result.append(System.lineSeparator());
				}
				lineBegin = lineEnd + 1;
			}
		}
		// the last part we add if there is something left
		if (lineBegin < iLength) {
			result.append(src.substring(lineBegin).getValue());
		}
		return values.string(result.toString());
	}

	public IValue format(IString s, IString dir, IInteger n, IString pad)
	//@doc{format -- return string of length n, with s placed according to dir (left/center/right) and padded with pad}
	{
	    StringBuffer res = new StringBuffer();
	    int sLen = s.length();
	    int nVal = n.intValue();
	    if(sLen > nVal){
	       return s;
	    }
	    int padLen = pad.length();
	    java.lang.String dirVal = dir.getValue();
	    int start;
	    
	    if(dirVal.equals("right"))
	       start = nVal - sLen;
	    else if(dirVal.equals("center"))
	       start = (nVal - sLen)/2;
	    else
	       start = 0;
	    
	    int i = 0;
	    while(i < start){
	         if(i + padLen < start){
	         	res.append(pad.getValue());
	         	i+= padLen;
	         } else {
	         	res.append(pad.substring(0, start - i).getValue());
	         	i += start - i;
	         }
	    }
	    res.append(s.getValue());
	    i = start + sLen;
	    while(i < nVal){
	         if(i + padLen < nVal){
	         	res.append(pad.getValue());
	         	i += padLen;
	         } else {
	         	res.append(pad.substring(0, nVal - i).getValue());
	         	i += nVal - i;
	         }
	    }
	    return values.string(res.toString());
	}
	
	public IValue isEmpty(IString s)
	//@doc{isEmpty -- is string empty?}
	{
		return values.bool(s.getValue().length() == 0);
	}

	public IValue reverse(IString s)
	//@doc{reverse -- return string with all characters in reverse order.}
	{
	   return s.reverse();
	}

	public IValue size(IString s)
	//@doc{size -- return the length of string s.}
	{
	  return values.integer(s.length());
	}

	public IValue startsWith(IString s, IString prefix)
	//@doc{startsWith -- return true if string s starts with the string prefix.}
	{
		if (prefix.length() == 0) {
			return values.bool(true);
		}
	  return values.bool(s.getValue().startsWith(prefix.getValue()));
	}

	public IValue substring(IString s, IInteger begin) {
		try {
			return s.substring(begin.intValue());
		} catch (IndexOutOfBoundsException e) {
			throw RuntimeExceptionFactory.indexOutOfBounds(begin);
		}
	}
	
	public IValue substring(IString s, IInteger begin, IInteger end) {
		try {
			return s.substring(begin.intValue(),end.intValue());
		} catch (IndexOutOfBoundsException e) {
			int bval = begin.intValue();
			IInteger culprit = (bval < 0 || bval >= s.length()) ? begin : end;
		    throw RuntimeExceptionFactory.indexOutOfBounds(culprit);
		}
	
	}
	
	public IValue toInt(IString s)
	//@doc{toInt -- convert a string s to integer}
	{
		try {
			java.lang.String sval = s.getValue();
			boolean isNegative = false;
			int radix = 10;
			
			if (sval.equals("0")) {
				return values.integer(0);
			}
			
			if (sval.startsWith("-")) {
				isNegative = true;
				sval = sval.substring(1);
			}
			if (sval.startsWith("0x") || sval.startsWith("0X")) {
				radix = 16;
				sval = sval.substring(2);
			} else if (sval.startsWith("0")) {
				radix = 8;
				sval = sval.substring(1);
			}
			BigInteger bi = new BigInteger(isNegative ? "-" + sval : sval, radix);
			return values.integer(bi.toString());
		}
		catch (NumberFormatException e){
			throw RuntimeExceptionFactory.illegalArgument(s, e.getMessage());
		}
	}
	
	public IValue toInt(IString s, IInteger r)
	{
		try {
			java.lang.String sval = s.getValue();
			boolean isNegative = false;
			int radix = r.intValue();
			
			if (sval.equals("0")) {
				return values.integer(0);
			}
			
			if (sval.startsWith("-")) {
				isNegative = true;
				sval = sval.substring(1);
			}
			BigInteger bi = new BigInteger(isNegative ? "-" + sval : sval, radix);
			return values.integer(bi.toString());
		}
		catch (NumberFormatException e){
			throw RuntimeExceptionFactory.illegalArgument();
		}
	}
	
	public IValue toReal(IString s)
	//@doc{toReal -- convert a string s to a real}
	{
		try {
			return values.real(s.getValue());
		}
		catch (NumberFormatException e){
			throw RuntimeExceptionFactory.illegalArgument();
		}
	}
	
	public IValue toReal(IRational s)
  //@doc{toReal -- convert a string s to a real}
  {
      return s.toReal(values.getPrecision());
  }

	// based on http://stackoverflow.com/a/6603018/11098
	public class ByteBufferBackedInputStream extends InputStream {
	  private final ByteBuffer buf;

	  public ByteBufferBackedInputStream(ByteBuffer buf) {
	    this.buf = buf;
	  }

	  public int read() throws IOException {
	    if (!buf.hasRemaining()) {
	      return -1;
	    }
	    return buf.get() & 0xFF;
	  }

	  public int read(byte[] bytes, int off, int len)
	      throws IOException {
	    if (!buf.hasRemaining()) {
	      return -1;
	    }

	    len = Math.min(len, buf.remaining());
	    buf.get(bytes, off, len);
	    return len;
	  }
	}	
	
	private static void copy(InputStream from, OutputStream to) throws IOException {
	  final byte[] buffer = new byte[FILE_BUFFER_SIZE];
		int read;
		while ((read = from.read(buffer, 0, buffer.length)) != -1) {
		  to.write(buffer, 0, read);
		}
	}
	private void copy(Reader from, Writer to) throws IOException {
		final char[] buffer = new char[FILE_BUFFER_SIZE / 2];
		int read;
		while ((read = from.read(buffer, 0, buffer.length)) != -1) {
		  to.write(buffer, 0, read);
		}
	}


	private String toBase64(InputStream src, int estimatedSize) throws IOException {
	  ByteArrayOutputStream result = new ByteArrayOutputStream(estimatedSize);
	  OutputStream encoder = Base64.getEncoder().wrap(result);
	  copy(src, encoder);
	  encoder.close();
	  return result.toString(StandardCharsets.ISO_8859_1.name());
	}

	public IString toBase64(IString in) {
	  try {
	      InputStream bytes = new ByteBufferBackedInputStream(StandardCharsets.UTF_8.encode(in.getValue()));
	      return values.string(toBase64(bytes, in.length() * 2));
	  } catch (IOException e) {
	      throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
	  }
	}

	public IString toBase64(ISourceLocation file) {
	    try (InputStream in = URIResolverRegistry.getInstance().getInputStream(file)) {
	        return values.string(toBase64(in, 1024));
	    }
	    catch (IOException e) {
	        throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
	    }
	}
	
	

	private void fromBase64(String src, OutputStream target) throws IOException {
	  InputStream bytes = new ByteBufferBackedInputStream(StandardCharsets.ISO_8859_1.encode(src));
	  copy(Base64.getDecoder().wrap(bytes), target);
	}

	public IString fromBase64(IString in) {
	    try {
	        ByteArrayOutputStream result = new ByteArrayOutputStream(in.length());
	        fromBase64(in.getValue(), result);
	        return values.string(result.toString(StandardCharsets.UTF_8.name()));
	    } catch (IOException e) {
	        throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
	    }
	}

	public IValue toLowerCase(IString s)
	//@doc{toLowerCase -- convert all characters in string s to lowercase.}
	{
	  return values.string(s.getValue().toLowerCase());
	}

	public IValue toUpperCase(IString s)
	//@doc{toUpperCase -- convert all characters in string s to uppercase.}
	{
	  return values.string(s.getValue().toUpperCase());
	}
	
	private boolean match(IString subject, int i, IString pattern){
		if(i + pattern.length() > subject.length())
			return false;
		for(int k = 0; k < pattern.length(); k++){
			if(subject.charAt(i) != pattern.charAt(k))
				return false;
			i++;
		}
		return true;
	}
	
	public IValue replaceAll(IString str, IString find, IString replacement){
		int fLength = find.length();
		if(fLength == 0){
			return str;
		}
		int iLength = str.length();
		StringBuilder b = new StringBuilder(iLength * 2); 
		int i = 0;
		boolean matched = false;
		while(i < iLength){
			if(match(str,i,find)){
				matched = true;
				b.append(replacement.getValue());
				i += Math.max(1, fLength);
			} else {
				b.appendCodePoint(str.charAt(i));
				i++;
			}
		}
		return (!matched) ? str : values.string(b.toString());
	}
	
	public IValue replaceFirst(IString str, IString find, IString replacement){
		int fLength = find.length();
		if(fLength == 0){
			return str;
		}
		int iLength = str.length();
		StringBuilder b = new StringBuilder(iLength * 2); 

		int i = 0;
		boolean matched = false;
		while(i < iLength){
			if(!matched && match(str,i,find)){
				matched = true;
				b.append(replacement.getValue());
				i += fLength;
				
			} else {
				b.appendCodePoint(str.charAt(i));
				i++;
			}
		}
		return (!matched) ? str : values.string(b.toString());
	}
	
	public IValue replaceLast(IString str, IString find, IString replacement){
		int fLength = find.length();
		if(fLength == 0){
			return str;
		}
		int iLength = str.length();
		StringBuilder b = new StringBuilder(iLength * 2); 
		
		int i = iLength - fLength;
		while(i >= 0){
			if(match(str,i,find)){
				b.append(str.substring(0, i).getValue());
				b.append(replacement.getValue());
				b.append(str.substring(i + fLength).getValue());
				return values.string(b.toString());
			}
			i--;
		}
		return str;
	}
	
	
	public IValue escape(IString str, IMap substitutions) {
		StringBuilder b = new StringBuilder(str.length() * 2); 
		
		int sLength = str.length();
		for (int c = 0; c < sLength; c++) {
			IString chr = str.substring(c, c+1);
			IString sub = (IString)substitutions.get(chr);

			if (sub != null) {
				b.append(sub.getValue());
			}
			else {
				b.append(chr.getValue());
			}
		}
		return values.string(b.toString());
	}
	
	public IValue contains(IString str, IString find){
		return values.bool(str.getValue().indexOf(find.getValue()) >= 0);
	}
	
	public IValue findAll(IString str, IString find){
		int iLength = str.length();
		int fLength = find.length();
		IListWriter w = values.listWriter();
		
		for(int i = 0; i <= iLength - fLength; i++){
			if(match(str, i, find)){
				w.append(values.integer(i));
			}
		}
		return w.done();
	}
	
	public IValue findFirst(IString str, IString find){
		int iLength = str.length();
		int fLength = find.length();
		
		for(int i = 0; i <= iLength - fLength; i++){
			if(match(str, i, find)){
				 return values.integer(i);
			}
		}
		return values.integer(-1);
	}
	
	public IValue findLast(IString str, IString find){
		int iLength = str.length();
		int fLength = find.length();
		
		for(int i = iLength - fLength; i >= 0; i--){
			if(match(str, i, find)){
				 return values.integer(i);
			}
		}
		return values.integer(-1);
	}
	
	/*
	 *  !!EXPERIMENTAL!!
	 * Tuple
	 */
	
	public IList fieldsOf(IValue v){
		if(!v.getType().isTuple())
			throw RuntimeExceptionFactory.illegalArgument(v, "argument of type tuple is required");
		ITuple tp = (ITuple) v;
		Type tt = tp.getType();
		int a = tt.getArity();
		IListWriter w = values.listWriter();
		for(int i = 0; i < a; i++){
			String fname = tt.getFieldName(i);
			if(fname == null)
				fname = "";
			w.append(values.string(fname));
		}
		return w.done();
	}
	
	/*
	 * ValueIO
	 */
	
	public IInteger getFileLength(ISourceLocation g) {
		if (g.getScheme().equals("file")) {
			File f = new File(g.getURI());
			if (!f.exists() || f.isDirectory()) { 
				throw RuntimeExceptionFactory.io(values.string(g.toString()));
			}
			
			return values.integer(f.length());
		}
		else {
			return values.integer(((IString) readFile(g)).getValue().getBytes().length);
		}
	}
	
	public void registerLocations(IString scheme, IString auth, IMap map) {
		URIResolverRegistry.getInstance().registerLogical(new LogicalMapResolver(scheme.getValue(), auth.getValue(), map));
	}
	
	public void unregisterLocations(IString scheme, IString auth) {
		URIResolverRegistry.getInstance().unregisterLogical(scheme.getValue(), auth.getValue());
	}
	
	public ISourceLocation resolveLocation(ISourceLocation loc) {
		try {
			return URIResolverRegistry.getInstance().logicalToPhysical(loc);
		} catch (IOException e) {
			throw RuntimeExceptionFactory.schemeNotSupported(loc);
		}
	}
	
	public IValue readBinaryValueFile(IValue type, ISourceLocation loc){
		if(trackIO) System.err.println("readBinaryValueFile: " + loc);

		TypeStore store = new TypeStore(RascalValueFactory.getStore());
		Type start = tr.valueToType((IConstructor) type, store);
		
		try (IValueInputStream in = constructValueReader(loc)) {
			IValue val = in.read();;
			if(val.getType().isSubtypeOf(start)){
				return val;
			} else {
			throw RuntimeExceptionFactory.io(values.string("Requested type " + start + ", but found " + val.getType()));
			}
		}
		catch (IOException e) {
			System.err.println("readBinaryValueFile: " + loc + " throws " + e.getMessage());
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
		catch (Exception e) {
			System.err.println("readBinaryValueFile: " + loc + " throws " + e.getMessage());
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}

    private IValueInputStream constructValueReader(ISourceLocation loc) throws IOException {
        URIResolverRegistry registry = URIResolverRegistry.getInstance();
        if (registry.supportsReadableFileChannel(loc)) {
            FileChannel channel = registry.getReadableFileChannel(loc);
            if (channel != null) {
                return new IValueInputStream(channel, values, TYPE_STORE_SUPPLIER);
            }
        }
        return new IValueInputStream(registry.getInputStream(loc), values, TYPE_STORE_SUPPLIER);
    }

    public IInteger __getFileSize(ISourceLocation loc) throws URISyntaxException, IOException {
        return __getFileSize(values, loc);
    }
    
	static public IInteger __getFileSize(IValueFactory values, ISourceLocation loc) throws URISyntaxException, IOException {
	    if (loc.getScheme().contains("compressed+")) {
	        loc = URIUtil.changeScheme(loc, loc.getScheme().replace("compressed+", ""));
	    }
	    IInteger result = values.integer(0);
	    try (InputStream in = URIResolverRegistry.getInstance().getInputStream(loc)) {
	        final byte[] buffer = new byte[FILE_BUFFER_SIZE];
	        int read;
	        while ((read = in.read(buffer, 0, buffer.length)) != -1) {
	            result = result.add(values.integer(read));
	        }
	        return result;
	    }
	}
	
	public IValue readTextValueFile(IValue type, ISourceLocation loc){
		if(trackIO) System.err.println("readTextValueFile: " + loc);
	  	TypeStore store = new TypeStore();
		Type start = tr.valueToType((IConstructor) type, store);
		
		try (Reader in = URIResolverRegistry.getInstance().getCharacterReader(loc, StandardCharsets.UTF_8)) {
			return new StandardTextReader().read(values, store, start, in);
		}
		catch (FactTypeUseException e) {
            throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
        } 
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
		catch (Exception e) {
		    throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}
	
	public IValue readTextValueString(IValue type, IString input) {
		TypeStore store = new TypeStore();
		Type start = tr.valueToType((IConstructor) type, store);
		
		try (StringReader in = new StringReader(input.getValue())) {
			return new StandardTextReader().read(values, store, start, in);
		} 
		catch (FactTypeUseException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		} 
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
		catch (Exception e) {
            throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
        }
	}

    public void writeBinaryValueFile(ISourceLocation loc, IValue value, IBool compression){
        if(trackIO) System.err.println("_writeBinaryValueFile: " + loc);
		try (IValueOutputStream writer = constructValueWriter(loc, CompressionRate.Normal)) {
		    writer.write(value);
		}
		catch (IOException ioex){
			throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()));
		}
    }

    private IValueOutputStream constructValueWriter(ISourceLocation loc, CompressionRate compression) throws IOException {
        URIResolverRegistry registry = URIResolverRegistry.getInstance();
        if (registry.supportsWritableFileChannel(loc)) {
            FileChannel channel = registry.getWriteableFileChannel(loc, false);
            if (channel != null) {
                return new IValueOutputStream(channel, values, compression);
            }
        }
        return new IValueOutputStream(registry.getOutputStream(loc, false), values, compression);
    }
	
    
    public void writeBinaryValueFile(ISourceLocation loc, IValue value, IConstructor compression){
    	if(trackIO) System.err.println("writeBinaryValueFile: " + loc);
        // ready for after new boot
		try (IValueOutputStream writer = constructValueWriter(loc, translateCompression(compression))) {
		    writer.write(value);
		}
		catch (IOException ioex){
			throw RuntimeExceptionFactory.io(values.string(ioex.getMessage()));
		}
	}

    private CompressionRate translateCompression(IConstructor compression) {
        switch (compression.getName()) {
            case "disabled": return CompressionRate.None;
            case "light": return CompressionRate.Light;
            case "normal": return CompressionRate.Normal;
            case "strong": return CompressionRate.Strong;
            case "extreme": return CompressionRate.Extreme;
            default: return CompressionRate.Normal;
        }
    }

    public void writeTextValueFile(ISourceLocation loc, IValue value){
        writeTextValueFile(values, trackIO, loc, value); 
    }
    
	static public void writeTextValueFile(IValueFactory values, boolean trackIO, ISourceLocation loc, IValue value){
		if(trackIO) System.err.println("writeTextValueFile: " + loc);
		try (Writer out = new OutputStreamWriter(URIResolverRegistry.getInstance().getOutputStream(loc, false), StandardCharsets.UTF_8)) {
			new StandardTextWriter().write(value, out);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string(e.getMessage()));
		}
	}
	
	public IBool rexpMatch(IString s, IString re) {
		if (Pattern.matches(re.getValue(), s.getValue())) {
			return values.bool(true);
		}
		else {
			return values.bool(false);
		}
	}

	public ISourceLocation uuid() {
		String uuid = UUID.randomUUID().toString();
		
		try {
			return values.sourceLocation("uuid",uuid,"");
		} 
		catch (URISyntaxException e) {
			assert false;
			throw RuntimeExceptionFactory.malformedURI("uuid://" + uuid);
		}
	}
	
	public IInteger uuidi() {
		UUID uuid = UUID.randomUUID();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(bytes);
		try {
			data.writeLong(uuid.getMostSignificantBits());
			data.writeLong(uuid.getLeastSignificantBits());
			return values.integer(bytes.toByteArray());
		} 
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(values.string("could not generate unique number " + uuid));
		}
	}
	

	// **** util::Random ***
	
	public IValue randomValue(IValue type, IInteger depth, IInteger width){
	    return randomValue(type, values.integer(random.nextInt()), depth, width);
	}
	
	public IValue randomValue(IValue type, IInteger seed, IInteger depth, IInteger width){
	    TypeStore store = new TypeStore(RascalValueFactory.getStore());
	    Type start = tr.valueToType((IConstructor) type, store);
	    Random random = new Random(seed.intValue());
	    return start.randomValue(random, values, store, Collections.emptyMap(), depth.intValue(), width.intValue());
	}

	// Utilities used by Graph
	//TODO: Why is this code in the library? This should be done in pure Rascal.

	private static class Distance{
	    public int intval;

	    Distance(int n){
	        intval = n;
	    }
	}

	private static class NodeComparator implements Comparator<IValue> {
	    private final Map<IValue,Distance> distance;

	    NodeComparator(Map<IValue,Distance> distance){
	        this.distance = distance;
	    }

	    public int compare(IValue arg0, IValue arg1) {
	        int d0 = distance.get(arg0).intval;
	        int d1 = distance.get(arg1).intval;

	        return d0 < d1 ? -1 : ((d0 == d1) ? 0 : 1);
	    }
	}

	public void sleep(IInteger seconds) {
	    try {
            TimeUnit.SECONDS.sleep(seconds.longValue());
        }
        catch (InterruptedException e) {
        }
	}

	public void watch(ISourceLocation src, IBool recursive, IFunction callback) {
		try {
			URIResolverRegistry.getInstance().watch(src, recursive.getValue(), (e) -> callback.call(convertChangeEvent(e)));
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(e.getMessage());
		}
	}

	private IValue convertChangeEvent(ISourceLocationChanged e) {
		Type changeEvent = store.lookupConstructors("changeEvent").iterator().next();
		
		
		return values.constructor(changeEvent, 
			e.getLocation(),
			convertChangeType(e.getChangeType()),
			convertFileType(e.getType())
		);
	}

	private IValue convertFileType(ISourceLocationType type) {
		Type file = store.lookupConstructors("file").iterator().next();
		Type directory = store.lookupConstructors("directory").iterator().next();
		
		switch (type) {
			case FILE:
				return values.constructor(file);
			case DIRECTORY:
				return values.constructor(directory);
		}

		throw RuntimeExceptionFactory.illegalArgument();
	}

	private IValue convertChangeType(ISourceLocationChangeType changeType) {
		Type deleted = store.lookupConstructors("deleted").iterator().next();
		Type created = store.lookupConstructors("created").iterator().next();
		Type modified = store.lookupConstructors("modified").iterator().next();

		switch (changeType) {
			case DELETED:
				return values.constructor(deleted);
			case CREATED:
				return values.constructor(created);
			case MODIFIED:
				return values.constructor(modified);
		}

		throw RuntimeExceptionFactory.illegalArgument();
	}

}

