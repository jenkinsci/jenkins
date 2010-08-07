/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
header {
  package hudson.model.label;
  import hudson.model.Label;
}

class LabelExpressionParser extends Parser;
options {
  defaultErrorHandler=false;
}

expr
returns [Label l]
  : l=term1
  | x=term1 IFF y=term1 EOF
    { l=x.iff(y); }
  ;

term1
returns [Label l]
  : l=term2
  | x=term2 AND y=term2 EOF
    { l=x.and(y); }
  ;

term2
returns [Label l]
  : LPAREN l=expr RPAREN
  | a:ATOM
    { l=LabelAtom.get(a.getText()); }
  ;

class LabelExpressionLexer extends Lexer;

AND:    "&&";
OR:     "||";
IFF:    "<->";
LPAREN: "(";
RPAREN: ")";

ATOM
    :   ('0'..'9'|'a'..'z'|'A'..'Z')+
    ;


WS
  : (' '|'\t')+
  ;
