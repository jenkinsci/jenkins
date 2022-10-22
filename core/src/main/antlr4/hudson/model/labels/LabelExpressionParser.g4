parser grammar LabelExpressionParser;

@ header
{
import hudson.model.Label;
}

options { tokenVocab = LabelExpressionLexer; }
// order of precedence is as per http://en.wikipedia.org/wiki/Logical_connective#Order_of_precedence

expr returns[Label l]
   : term1
   { $l=$term1.ctx.l; } EOF
   ;

term1 returns[Label l] locals[Label r]
   : term2
   { $l=$term2.ctx.l; } (IFF term2
   { $r=$term2.ctx.l; $l=$l.iff($r); })*
   ;
   // (a->b)->c != a->(b->c) (for example in case of a=F,b=T,c=F) so don't allow chaining
   
term2 returns[Label l] locals[Label r]
   : term3
   { $l=$term3.ctx.l; } (IMPLIES term3
   { $r=$term3.ctx.l; $l=$l.implies($r); })?
   ;

term3 returns[Label l] locals[Label r]
   : term4
   { $l=$term4.ctx.l; } (OR term4
   { $r=$term4.ctx.l; $l=$l.or($r); })*
   ;

term4 returns[Label l] locals[Label r]
   : term5
   { $l=$term5.ctx.l; } (AND term5
   { $r=$term5.ctx.l; $l=$l.and($r); })*
   ;

term5 returns[Label l]
   : term6
   { $l=$term6.ctx.l; }
   | NOT term6
   { $l=$term6.ctx.l; $l=$l.not(); }
   ;

term6 returns[Label l]
   : LPAREN term1 RPAREN
   { $l=$term1.ctx.l ; $l=$l.paren(); }
   | ATOM
   { $l=LabelAtom.get($ATOM.getText()); }
   | STRINGLITERAL
   { $l=LabelAtom.get(hudson.util.QuotedStringTokenizer.unquote($STRINGLITERAL.getText())); }
   ;

