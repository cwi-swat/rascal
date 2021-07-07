/*******************************************************************************
 * Copyright (c) 2009-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Mark Hills - Mark.Hills@cwi.nl (CWI)
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
*******************************************************************************/
package org.rascalmpl.semantics.dynamic;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.rascalmpl.exceptions.ImplementationError;
import org.rascalmpl.interpreter.IEvaluator;
import org.rascalmpl.interpreter.result.Result;
import org.rascalmpl.interpreter.staticErrors.DateTimeSyntax;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IDateTime;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.exceptions.FactParseError;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.io.StandardTextReader;

public abstract class JustTime extends org.rascalmpl.ast.JustTime {

	static public class Lexical extends org.rascalmpl.ast.JustTime.Lexical {

		public Lexical(ISourceLocation __param1, IConstructor tree, String __param2) {
			super(__param1, tree, __param2);
		}

		@Override
		public Result<IValue> interpret(IEvaluator<Result<IValue>> __eval) {
			// Time is of the form $T<time>
			String timePart = this.getString().substring(2);
			return createVisitedTime(__eval, timePart.substring(0, timePart.length() - 1), this);
		}
		
		private static final Pattern MILLI_SECONDS = Pattern.compile("\\.([0-9]+)");
		private static final Pattern ZONE_OFFSET = Pattern.compile("([\\-+]([0-9][0-9]):?([0-9][0-9])?|Z)");

		private Result<IValue> createVisitedTime(IEvaluator<Result<IValue>> eval, String timePart, org.rascalmpl.ast.JustTime.Lexical x) {
			try {
				int hourPart;
				int minutePart;
				int secondsPart;
				if (!timePart.contains(":")) {
					hourPart = Integer.parseInt(timePart.substring(0, 2));
					minutePart = Integer.parseInt(timePart.substring(2, 4));
					secondsPart = Integer.parseInt(timePart.substring(4, 6));
				}
				else {
					hourPart = Integer.parseInt(timePart.substring(0, 2));
					minutePart = Integer.parseInt(timePart.substring(3, 5));
					secondsPart = Integer.parseInt(timePart.substring(6, 8));
				}
				int millisecondsPart = 0;
				Matcher milliMatcher = MILLI_SECONDS.matcher(timePart);
				if (milliMatcher.find()) {
					String subPart = milliMatcher.group(1);
					// we have to right pad with zeros
					subPart = String.format("%-3s", subPart).replace(' ', '0');
					millisecondsPart = Integer.parseInt(subPart);
				}
				IDateTime result;
				Matcher zoneMatcher = ZONE_OFFSET.matcher(timePart);
				if (zoneMatcher.find()) {
					int factor = timePart.contains("-") ? -1 : 1;

					int timeZoneHours = 0;
					int timeZoneSeconds = 0;
					if (zoneMatcher.groupCount() >= 2) {
						timeZoneHours = factor * Integer.parseInt(zoneMatcher.group(2));
					}
					if (zoneMatcher.groupCount() == 3) {
						timeZoneSeconds = factor * Integer.parseInt(zoneMatcher.group(3));
					}
					result = VF.time(hourPart, minutePart, secondsPart, millisecondsPart, timeZoneHours, timeZoneSeconds); 
				}
				else {
					result = VF.time(hourPart, minutePart, secondsPart, millisecondsPart);
				}
				return makeResult(TF.dateTimeType(), result, eval);
			} catch (FactTypeUseException e) {
				throw new DateTimeSyntax(e.getMessage(), eval.getCurrentAST().getLocation());
			}
		}
	}

	public JustTime(ISourceLocation __param1, IConstructor tree) {
		super(__param1, tree);
	}

}
