package org.rascalmpl.library.lang.csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.library.Prelude;
import org.rascalmpl.types.TypeReifier;
import org.rascalmpl.unicode.UnicodeOutputStreamWriter;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.ICollection;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.IWriter;
import io.usethesource.vallang.exceptions.FactParseError;
import io.usethesource.vallang.exceptions.UnexpectedTypeException;
import io.usethesource.vallang.io.StandardTextReader;
import io.usethesource.vallang.type.DefaultTypeVisitor;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class IO {
    protected static final TypeFactory types = TypeFactory.getInstance();


    protected final IValueFactory values;
    protected final TypeReifier tr;

    public IO(IValueFactory values){
        this.values = values;
        this.tr = new TypeReifier(values);
    }

    /*
     * Read a CSV file
     */
    public IValue readCSV(ISourceLocation loc, IBool header, IString separator, IString encoding) {
        return read(null, loc, header, separator, encoding);
    }

    public IValue readCSV(IValue result, ISourceLocation loc, IBool header, IString separator, IString encoding) {
        return read(result, loc, header, separator, encoding);
    }

    public IValue getCSVType(ISourceLocation loc, IBool header, IString separator, IString encoding) {
        return computeType(loc, header, separator, encoding, (IValue v) -> new TypeReifier(values).typeToValue(v.getType(), new TypeStore(), values.mapWriter().done()));
    }

    public void writeCSV(IValue schema, IValue rel, ISourceLocation loc, IBool header, IString separator, IString encoding) {
        writeCSV(rel, loc, header, separator, encoding, tr.valueToType((IConstructor)schema, new TypeStore()));
    }

    //////

    protected IValue read(IValue resultTypeConstructor, ISourceLocation loc, IBool header, IString separator, IString encoding) {
        CSVReader reader = new CSVReader(header, separator);

        Type resultType = types.valueType();
        TypeStore store = new TypeStore();
        if (resultTypeConstructor != null && resultTypeConstructor instanceof IConstructor) {
            resultType = tr.valueToType((IConstructor)resultTypeConstructor, store);
        }
        Type actualType = resultType;
        while (actualType.isAliased()) {
            actualType = actualType.getAliased();
        }
        try (Reader charReader = URIResolverRegistry.getInstance().getCharacterReader(loc, encoding.getValue())) {
            if (actualType.isTop()) {
                return reader.readInferAndBuild(charReader, store);
            }
            else {
                return reader.readAndBuild(charReader, actualType, store);
            }
        }
        catch (IOException e){
            throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
        }
    }


    protected IValue computeType(ISourceLocation loc, IBool header, IString separator, IString encoding, Function<IValue, IValue> valueToTypeConverter) {
        return valueToTypeConverter.apply(this.read(null, loc, header, separator, encoding));
    }

    protected class CSVReader {
        private final StandardTextReader pdbReader;
        private final int separator;      // The separator to be used between fields
        private final boolean header;     // Does the file start with a line defining field names?

        public CSVReader(IBool header, IString separator) {
            this.separator = separator == null ? ',' : separator.charAt(0);
            this.header = header == null ? true : header.getValue();
            this.pdbReader = new StandardTextReader();
        }


        private String[] readFirstRecord(FieldReader reader) throws IOException {
            List<String> result = new ArrayList<String>();
            if (reader.hasRecord()) {
                while (reader.hasField()) {
                    result.add(reader.getField());
                }
                return result.toArray(new String[0]);
            }
            return new String[0];
        }

        private void collectFields(FieldReader reader, final String[] currentRecord, int currentRecordCount) throws IOException {
            int recordIndex = 0;
            while (reader.hasField()) {
                if (recordIndex < currentRecord.length) {
                    currentRecord[recordIndex++] = reader.getField();
                }
                else {
                    recordIndex++;  // count how many collumns to many we have
                }
            }
            if (recordIndex != currentRecord.length) {
                throw RuntimeExceptionFactory.illegalTypeArgument("Arities of actual type and requested type are different (expected: " + currentRecord.length + ", found: " + recordIndex + ") at record: " + currentRecordCount, null, null);
            }
        }



        private IValue readInferAndBuild(Reader stream, TypeStore store) throws IOException {
            FieldReader reader = new FieldReader(stream, separator);

            boolean first = header;
            final String[] currentRecord = readFirstRecord(reader);

            final String[] labels = new String[currentRecord.length]; 
            for (int l =0; l < labels.length; l++) {
                if (header) {
                    labels[l] = normalizeLabel(currentRecord[l], l);
                }
                else {
                    labels[l] = "field" + l;
                }
            }

            final Type[] expectedTypes = new Type[currentRecord.length];
            Arrays.fill(expectedTypes, types.valueType());

            final Type[] currentTypes = new Type[currentRecord.length]; 
            Arrays.fill(currentTypes, types.voidType());

            List<IValue[]> records = new LinkedList<>();
            int currentRecordCount = 1;

            do {
                if (first) {
                    first = false;
                    continue;
                }
                collectFields(reader, currentRecord, currentRecordCount++);

                IValue[] tuple = new IValue[currentRecord.length];
                parseRecordFields(currentRecord, expectedTypes, store, tuple, false);
                records.add(tuple);

                // update infered type
                for (int i = 0; i < currentTypes.length; i++){
                    if (tuple[i] == null) continue;
                    currentTypes[i] = currentTypes[i].lub(tuple[i].getType());
                    if(currentTypes[i].isTop()) {
                        currentTypes[i] = types.stringType();
                    }
                }
            } while(reader.hasRecord());

            // done reading records, lets infer and build
            for (int i = 0; i < currentTypes.length; i++){
                if(currentTypes[i].isBottom()) {
                    // in case of an completly empty row
                    currentTypes[i] = types.stringType();
                }
            }
            Type tupleType = types.tupleType(currentTypes, labels);

            IWriter<ISet> result = values.setWriter();
            for (IValue[] rec : records) {
                result.insert(createTuple(tupleType, rec));
            }
            return result.done();
        }
        private IValue createTuple(Type tupleType, IValue[] rec) {
            // do some fixing of empties and value => string
            for (int i = 0 ; i < rec.length; i++) {
                if (rec[i] == null) {
                    rec[i] = defaultValue(tupleType.getFieldType(i));
                }
                else if (tupleType.getFieldType(i).isString() && !rec[i].getType().isString()) {
                    rec[i] = values.string(rec[i].toString());
                }
            }
            return values.tuple(rec);
        }

        private IValue readAndBuild(Reader stream, Type actualType, TypeStore store) throws IOException {
            FieldReader reader = new FieldReader(stream, separator);
            IWriter<?> result = actualType.isListRelation() ? values.listWriter() : values.setWriter();

            boolean first = header;
            Type tupleType = actualType.getElementType();
            Type[] expectedTypes = new Type[tupleType.getArity()];
            for (int i = 0; i < expectedTypes.length; i++) {
                expectedTypes[i] = tupleType.getFieldType(i);
            }

            int currentRecordCount = 1;
            final String[] currentRecord = new String[expectedTypes.length];
            final IValue[] tuple = new IValue[expectedTypes.length];
            while (reader.hasRecord()) {
                collectFields(reader, currentRecord, currentRecordCount++);
                if (first) {
                    first = false;
                    continue;
                }
                parseRecordFields(currentRecord, expectedTypes, store, tuple, true);
                if (result instanceof IListWriter) {
                    ((IListWriter)result).append(values.tuple(tuple));
                }
                else {
                    result.insert(values.tuple(tuple));
                }
            }
            return result.done();
        }

        private void parseRecordFields(final String[] fields, final Type[] expectedTypes, TypeStore store, IValue[] result, boolean replaceEmpty) throws IOException  {
            for (int i=0; i < fields.length; i++) {
                final String field = fields[i];
                final Type currentType = expectedTypes[i];

                if (field.isEmpty()) {
                    if (replaceEmpty) {
                        result[i] = defaultValue(currentType);
                    }
                    else {
                        result[i] = null;
                    }
                    continue;
                }

                result[i] = currentType.accept(new DefaultTypeVisitor<IValue, RuntimeException>((IValue)null) {
                    @Override
                    public IValue visitString(Type type) throws RuntimeException {
                        return values.string(field);
                    }
                    @Override
                    public IValue visitInteger(Type type) throws RuntimeException {
                        try {
                            return values.integer(field);
                        }
                        catch (NumberFormatException nfe) {
                            throw RuntimeExceptionFactory.illegalTypeArgument(currentType.toString(), null, null, "Invalid int \"" + field + "\" for requested field " + currentType);
                        }
                    }
                    @Override
                    public IValue visitReal(Type type) throws RuntimeException {
                        try {
                            return values.real(field);
                        }
                        catch (NumberFormatException nfe) {
                            throw RuntimeExceptionFactory.illegalTypeArgument("Invalid real \"" + field + "\" for requested field " + currentType, null, null);
                        }
                    }
                    @Override
                    public IValue visitBool(Type type) throws RuntimeException {
                        if (field.equalsIgnoreCase("true")) {
                            return values.bool(true);
                        }
                        if (field.equalsIgnoreCase("false")) {
                            return values.bool(false);
                        }
                        throw RuntimeExceptionFactory.illegalTypeArgument("Invalid bool \"" + field + "\" for requested field " + currentType, null, null);
                    }

                });

                if (result[i] == null) {
                    // use pdb reader to deserialize the other types
                    StringReader in = new StringReader(field);
                    try {
                        result[i] = pdbReader.read(values, store, currentType, in);
                        if (currentType.isTop() && result[i].getType().isString()) {
                            result[i] = values.string(field);
                        }
                    }
                    catch (UnexpectedTypeException ute) {
                        throw RuntimeExceptionFactory.illegalTypeArgument("Invalid field \"" + field + "\" (" + ute.getExpected() + ") for requested field " + ute.getGiven(), null, null);
                    }
                    catch (FactParseError | NumberFormatException ex) {
                        if (currentType.isTop()) {
                            // our text reader is quite strict about booleans
                            // in csv's (for example those produced by R), TRUE is also a boolean
                            if (field.equalsIgnoreCase("true")) {
                                result[i] = values.bool(true);
                            }
                            else if (field.equalsIgnoreCase("false")) {
                                result[i] = values.bool(true);
                            }
                            else {
                                // it is an actual string
                                result[i] = values.string(field);
                            }
                        }
                        else if (currentType.isDateTime()) {
                            try {
                                // lets be a bit more flexible than rascal's string reader is.
                                // 2012-06-24T00:59:56Z
                                result[i] = Prelude.parseDateTime(values, values.string(field), values.string("yyyy-MM-dd'T'HH:mm:ssZZZZZ"));
                            }
                            catch (Throwable th) {
                                throw RuntimeExceptionFactory.illegalTypeArgument("Invalid datetime: \"" + field + "\" (" + th.getMessage() + ")", null, null);
                            }
                        }
                        else {
                            throw RuntimeExceptionFactory.illegalTypeArgument("Invalid field \"" + field + "\" is not a " + currentType, null, null);
                        }
                    }
                    finally {
                        in.close();
                    }
                }
            }
        }

        private IValue defaultValue(Type targetType) {
            IValue result = targetType.accept(new DefaultTypeVisitor<IValue, RuntimeException>(null) {
                @Override
                public IValue visitBool(Type type) throws RuntimeException {
                    return values.bool(false);
                }
                @Override
                public IValue visitDateTime(Type type) throws RuntimeException {
                    return values.datetime(-1);
                }
                @Override
                public IValue visitInteger(Type type) throws RuntimeException {
                    return values.integer(0);
                }
                @Override
                public IValue visitList(Type type) throws RuntimeException {
                    return values.list();
                }
                @Override
                public IValue visitMap(Type type) throws RuntimeException {
                    return values.mapWriter().done();
                }
                @Override
                public IValue visitNumber(Type type) throws RuntimeException {
                    return values.integer(0);
                }
                @Override
                public IValue visitRational(Type type) throws RuntimeException {
                    return values.rational(0, 1);
                }
                @Override
                public IValue visitReal(Type type) throws RuntimeException {
                    return values.real(0);
                }
                @Override
                public IValue visitSet(Type type) throws RuntimeException {
                    return values.set();
                }
                @Override
                public IValue visitSourceLocation(Type type) throws RuntimeException {
                    return values.sourceLocation(URIUtil.invalidURI());
                }
                @Override
                public IValue visitString(Type type) throws RuntimeException {
                    return values.string("");
                }
                @Override
                public IValue visitTuple(Type type) throws RuntimeException {
                    IValue[] elems = new IValue[type.getArity()];
                    for (int i =0; i < elems.length; i++) {
                        elems[i] = type.getFieldType(i).accept(this);
                        if (elems[i] == null)
                            return null;
                    }
                    return values.tuple(elems);
                }
                @Override
                public IValue visitValue(Type type) throws RuntimeException {
                    return values.string("");
                }
            });
            if (result != null)
                return result;
            throw RuntimeExceptionFactory.illegalTypeArgument("Cannot create a default value for an empty field of type " + targetType, null, null);
        }


        /**
         * Auxiliary class to read fields from an input stream.
         *
         */
        private class FieldReader {
            int lastChar = ';';
            int separator = ';';
            Reader in;
            boolean startOfLine = true;

            FieldReader(Reader reader, int sep) throws IOException{
                this.in = reader;
                this.separator = sep;
                startOfLine = true;
                lastChar = reader.read();
            }

            private boolean isEOL(int c) {
                return c == '\n' || c == '\r';
            }

            /**
             * @return true if the current record has another field left to be read.
             * @throws IOException
             */
            boolean hasField() throws IOException{
                if(startOfLine)
                    return true;
                if(lastChar == separator){
                    lastChar = in.read();
                    return true; //lastChar != -1;
                }
                return false;
            }

            /**
             * @return true if the current stream has another record to be read.
             * @throws IOException
             */
            boolean hasRecord() throws IOException{
                if(startOfLine)
                    return true;
                while(isEOL(lastChar))
                    lastChar = in.read();
                startOfLine = true;
                return lastChar != -1;
            }

            /**
             * @return The next field from the input stream.
             * @throws IOException
             */
            String getField() throws IOException{
                startOfLine = false;
                StringWriter sw = new StringWriter();
                if(lastChar == '"'){
                    lastChar = in.read();
                    while (lastChar != -1){
                        if(lastChar == '"'){
                            lastChar = in.read();
                            if(lastChar == '"'){
                                sw.append('"');
                                lastChar = in.read();
                            } else
                                break;
                        } else {
                            sw.append((char)lastChar);
                            lastChar = in.read();
                        }
                    }
                    assert lastChar == separator || isEOL(lastChar) || lastChar == -1;
                    return sw.toString();
                }
                while ((lastChar != -1) && (lastChar != separator) && !isEOL(lastChar)){
                    sw.append((char)lastChar);
                    lastChar = in.read();
                }
                return sw.toString();
            }

        }
    }

    protected void writeCSV(IValue rel, ISourceLocation loc, IBool header, IString separator, IString encoding, Type paramType) {
        String sep = separator != null ? separator.getValue() : ",";
        Boolean head = header != null ? header.getValue() : true;

        if(!rel.getType().isRelation() && !rel.getType().isListRelation() || !(rel instanceof IList || rel instanceof ISet)){
            throw RuntimeExceptionFactory.illegalTypeArgument("A relation type is required instead of " + paramType, null, null);
        }

        try (Writer out = new BufferedWriter(new UnicodeOutputStreamWriter(URIResolverRegistry.getInstance().getOutputStream(loc, false), encoding.getValue(), false))){
            int nfields = rel instanceof IList ? ((IList)rel).asRelation().arity() : ((ISet)rel).asRelation().arity();
           
            if (head) {
                for (int i = 0; i < nfields; i++){
                    if (i > 0) {
                        out.write(sep);
                    }
                    
                    out.write(paramType.hasFieldNames() ? paramType.getFieldName(i) : ("field" + i));
                }
                
                out.write('\n');
            }

            Pattern escapingNeeded = Pattern.compile("[\\n\\r\"\\x" + Integer.toHexString(separator.charAt(0)) + "]");

            for (IValue v : (ICollection<?>) rel) {
                ITuple tup = (ITuple) v;
                boolean firstTime = true;
                for (IValue w : tup){
                    if (firstTime) {
                        firstTime = false;
                    }
                    else {
                        out.write(sep);
                    }

                    String s;
                    if (w.getType().isString()){
                        s = ((IString)w).getValue();
                    }
                    else {
                        s = w.toString();
                    }
                    if (escapingNeeded.matcher(s).find()){
                        s = s.replaceAll("\"", "\"\"");
                        out.write('"');
                        out.write(s);
                        out.write('"');
                    } else
                        out.write(s);
                }
                out.write('\n');
            }
        }
        catch(IOException e){
            throw RuntimeExceptionFactory.io(values.string(e.getMessage()), null, null);
        }
    }

    /**
     * Normalize a label in the header for use in the relation type.
     *
     * @param label the string found in the header
     * @param pos position in the header
     * @return the label (with non-fieldname characters removed) or "field<pos>" when empty
     */
    private String normalizeLabel(final String label, final int pos) {
        final String normalizedLabel = label.replaceAll("[^a-zA-Z0-9]+", "");

        if (!normalizedLabel.isEmpty()) {
            return normalizedLabel;
        } else {
            return "field" + pos;
        }
    }


}


