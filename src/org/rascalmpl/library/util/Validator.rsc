@doc{
This module defines a generic validator function that can convert values of the `node` type to instances of abstract `data` type constructors.

The intended use-case is to read structured data externally, say an XML or JSON or YAML file, as generic `node` values and then
to use the `validate` function to map the untyped representation to a typed representation, if it can be validated accordingly.
}
module util::Validator

import Type;
import Node;
import List;
import Exception;

data RuntimeException = invalid(str \type, value v, list[value] path=[]);
   
private data RuntimeException = none();

@doc{The general and simple validation case is when a value's run-time type already matches the expected static type}
@memo
&T validate(type[&T] expected, value v, list[value] path=[]) {
    if (&T x := v) {
    	return x;
  	}
  	else {
  	   fail validate;
  	}
}

@doc{To validate nodes we can try whether or not it can be matched to a constructor of a defined data type with the same name and (resp. validating) children.}
@memo
&T validate(type[&T] expected, node v, list[value] path = []) {
    if (expected.symbol == \node()) {
        return v;
    }
    
  	if (def:adt(_, _) := expected.symbol, grammar := expected.definitions) {
	    RuntimeException msg = none();
	    name = getName(v);
	
        // there may be several constructors with this name; we try them all, backtracking over already validated sub-values:
        for (/\cons(label(name, def), list[Symbol] symbols, list[Symbol] kwTypes, set[Attr] _) := grammar[def]?[]) {
            children = getChildren(v);
            params = getKeywordParameters(v);
        
            if (size(children) != size(symbols)) {
                continue;
            }
            
            try {
                // for the recursion it's important that we @memo the results to avoid rework in the next cycle of the surrounding for loop
                children = [validate(type(sym, grammar), children[i], path=path+[i]) | i <- index(children), label(_, Symbol sym) := symbols[i]];
                
                // similarly for recursion into the keyword parameters, we @memo this function to make sure we don't do sub-trees again and again:
                params = (key:validate(type(sym, grammar), params[key], path=path+[key]) | key <- params, [*_,label(key, sym),*_] := kwTypes || sym := \value());
                
                // TODO: make a more specific and faster version of `make` that can apply a specific constructor directly
                return make(expected, name, children, params);
            } 
            catch RuntimeException e:invalid(_,_): {
                msg = e;
                continue;
            }
            catch RuntimeException e:IllegalArgument(_): {
                // pretty sure this can never happen, but `make` does potentially throw this in the general case...
                msg = e;
                continue;
            }
        }
   
        if (msg != none()) {
            throw msg;
        }
        else {
            fail validate;
        }
    }
    
    fail validate;
}
 
@doc{if a (sub)value can not be validated we report the expected type, the not-matching value and the path that led us there} 
default &T validate(type[&T] expected, value v, list[value] path=[]) {
	throw invalid("<expected>", v, path=path);
} 

test bool simpleInt() {
    value x = 1;
  
    return int _ := validate(#int, x);
}

test bool defaultNode() {
    value x = "hello"();
    
    return node _ := validate(#node, x);
}

test bool adtTest() {
   value x = "invalid"("XXX", [[[]]],path=[1,0,0]);
   
   return RuntimeException _ := validate(#RuntimeException, x);
}

test bool adtTestFail() {
  value x = "invali"("XXX", [[[]]],path=[1,0,0]);
  
  try {
      validate(#RuntimeException, x);
      return false;
  }
  catch invalid(_,_) : 
      return true;
  
}

test bool adtTestFailNested() {
  value x = "invalid"(2, [[[]]],path=[1,0,0]);
  
  try {
      validate(#RuntimeException, x);
      return false;
  }
  catch invalid("str",2,path=[0]) : 
      return true;
  
}

test bool adtTestFailKeyword() {
  value x = "invalid"("hello", [[[]]],path="[1,0,0]");
  
  try {
      validate(#RuntimeException, x);
      return false;
  }
  catch invalid("list[value]","[1,0,0]",path=["path"]) : 
      return true;
  
}
