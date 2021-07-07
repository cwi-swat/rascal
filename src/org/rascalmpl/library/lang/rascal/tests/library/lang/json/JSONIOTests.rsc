module lang::rascal::tests::library::lang::json::JSONIOTests

import String;
import Node;
import Type;
import lang::json::IO;
import IO;
import util::UUID;

loc targetFile = |test-temp:///test-<"<uuidi()>">.json|;

bool jsonFeaturesSupported(value v) {
    for (/num r := v, size("<r>") > 10) {
         // json can only contain double precision video's
         // so let's ignore the cases
        return false;
    }
    for (/node n := v, getAnnotations(n) != ()) {
         // json reader/writer can't handle annotations at the moment
        return false;
    }
    for (/datetime dt := v, !dt.isDateTime) {
        // json can only deal with full date timestamp
        return false;
    }
    return true;
}    


bool writeRead(type[&T] returnType, &T dt) {
    if (!jsonFeaturesSupported(dt)) {
        return true;
    }
    json = toJSON(dt);
    return fromJSON(returnType, json) == dt;
}
	
data DATA1 = f1(int n) | f1(int n, str s) | rec1(DATA1 d1, DATA1 d2);

data DATA2 = f2(int n, str kw = "abc") | f2(int n, str s) | rec2(DATA2 d1, DATA2 d2, int n = 0);

test bool jsonWithBool1(bool dt) = writeRead(#bool, dt);
test bool jsonWithInt1(int dt) = writeRead(#int, dt);
test bool jsonWithReal1(real dt) = writeRead(#real, dt);
test bool jsonWithRat1(rat dt) = writeRead(#rat, dt);
test bool jsonWithNum1(num dt) = writeRead(#num, dt);

test bool jsonWithLoc1(loc dt) = writeRead(#loc, dt);
test bool jsonWithStr1(str dt) = writeRead(#str, dt);
test bool jsonWithDatetime1(datetime dt) = writeRead(#datetime, dt);
test bool jsonWithList1(list[int] dt) = writeRead(#list[int], dt);
test bool jsonWithSet1(set[int] dt) = writeRead(#set[int], dt);
test bool jsonWithMap1(map[int, int]  dt) = writeRead(#map[int,int], dt);
test bool jsonWithNode1(node  dt) = writeRead(#node, dt);

test bool jsonWithDATA11(DATA1 dt) = writeRead(#DATA1, dt);
test bool jsonWithDATA21(DATA2 dt) = writeRead(#DATA2, dt);

test bool jsonRandom1(value dt) = writeRead(#value, dt);

data D 
    = date(datetime dt)
    | uri(loc l)
    | string(str s)
    | integer(int n)
    | float(real r)
    | lists(list[D] ls)
    | maps(map[str,str] strmaps)
    | maps2(map[str,D] dmaps)
    | nested(D d1, D d2)
    | kwparams(int x = 2, D d = integer(0))
    ;
    
test bool jsonStreaming1(D dt) {
    if (!jsonFeaturesSupported(dt)) {
        return true;
    }
    writeJSON(targetFile, dt, dateTimeAsInt=false);
    return readJSON(#D, targetFile) == dt;
}

test bool jsonStreaming2(D dt) {
    if (!jsonFeaturesSupported(dt)) {
        return true;
    }
    dt = visit(dt) {
        // workaround when we store date times as int, we lose timezone information
        case datetime d => d[timezoneOffsetHours = 0][timezoneOffsetMinutes=0]
            when !d.isDate
    };
    writeJSON(targetFile, dt, dateTimeAsInt=true);
    return readJSON(#D, targetFile) == dt;
}

