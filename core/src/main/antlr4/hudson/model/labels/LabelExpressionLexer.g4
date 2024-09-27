lexer grammar LabelExpressionLexer;

AND
   : '&&'
   ;

OR
   : '||'
   ;

NOT
   : '!'
   ;

IMPLIES
   : '->'
   ;

IFF
   : '<->'
   ;

LPAREN
   : '('
   ;

RPAREN
   : ')'
   ;

fragment IDENTIFIER_PART
   : ~ ('&' | '|' | '!' | '<' | '>' | '(' | ')' | ' ' | '\t' | '"' | '\'' | '-')
   ;

ATOM
/*
    the real check of valid identifier happens in LabelAtom.get()

    https://www.antlr2.org/doc/lexer.html#usingexplicit
    If we are seeing currently a '-', we check that the next char is not a '>' which will be a IMPLIES.
    Otherwise the ATOM and the IMPLIES will collide and expr like a->b will just be parsed as ATOM (without spaces)
*/
   
   : (
   { _input.LA(2) != '>' }? '-' | IDENTIFIER_PART)+
   ;

WS
   : (' ' | '\t')+ -> skip
   ;

STRINGLITERAL
   : '"' ('\\' ('b' | 't' | 'n' | 'f' | 'r' | '"' | '\'' | '\\') /* escape */
   
   | ~ ('\\' | '"' | '\r' | '\n'))* '"'
   ;

