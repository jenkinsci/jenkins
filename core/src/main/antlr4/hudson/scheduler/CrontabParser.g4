parser grammar CrontabParser;


options { tokenVocab = CrontabLexer; superClass = BaseParser; }
startRule[CronTab table]
   : expr[0]
   { $table.bits[0]=$expr.ctx.bits; } WS expr[1]
   { $table.bits[1]=$expr.ctx.bits; } WS expr[2]
   { $table.bits[2]=$expr.ctx.bits; } WS expr[3]
   { $table.bits[3]=$expr.ctx.bits; } WS expr[4]
   { $table.dayOfWeek=(int)$expr.ctx.bits; } EOF
   | (AT ('yearly'
   {
           $table.set("H H H H *",getHashForTokens());
         } | 'annually'
   {
           $table.set("H H H H *",getHashForTokens());
         } | 'monthly'
   {
           $table.set("H H H * *",getHashForTokens());
         } | 'weekly'
   {
           $table.set("H H * * H",getHashForTokens());
         } | 'daily'
   {
           $table.set("H H * * *",getHashForTokens());
         } | 'midnight'
   {
           $table.set("H H(0-2) * * *",getHashForTokens());
         } | 'hourly'
   {
           $table.set("H * * * *",getHashForTokens());
         }))
   ;

expr[int field] returns[long bits=0] locals[long lhs, long rhs=0]
   : term[field]
   { $lhs = $term.ctx.bits; } (OR expr[field]
   { $rhs = $expr.ctx.bits; })?
   {
  $bits = $lhs|$rhs;
 }
   ;

term[int field] returns[long bits=0] locals[int d=NO_STEP, int s, int e]
   : token
   { $s=$token.ctx.value; } MINUS token
   { $e=$token.ctx.value; } (DIV token
   { $d=$token.ctx.value; })?
   {
     $bits = doRange($s,$e,$d,$field);
   }
   | token
   {
     rangeCheck($token.ctx.value,$field);
     $bits = 1L<<$token.ctx.value;
   }
   | STAR (DIV token
   { $d=$token.ctx.value; })?
   {
     $bits = doRange($d,$field);
   }
   | 'H' LPAREN token
   { $s=$token.ctx.value; } MINUS token
   { $e=$token.ctx.value; } RPAREN (DIV token
   { $d=$token.ctx.value; })?
   {
     $bits = doHash($s,$e,$d,$field);
   }
   | 'H' (DIV token
   { $d=$token.ctx.value; })?
   {
     $bits = doHash($d,$field);
   }
   ;

token returns[int value=0]
   : TOKEN
   {
     $value = Integer.parseInt($TOKEN.getText());
   }
   ;

