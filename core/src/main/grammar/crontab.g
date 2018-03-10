/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
  package hudson.scheduler;
}

class CrontabParser extends Parser("BaseParser");
options {
  defaultErrorHandler=false;
}

startRule [CronTab table]
throws ANTLRException
{
  long m,h,d,mnth,dow;
}
  : m=expr[0] WS h=expr[1] WS d=expr[2] WS mnth=expr[3] WS dow=expr[4] EOF
  {
    table.bits[0]=m;
    table.bits[1]=h;
    table.bits[2]=d;
    table.bits[3]=mnth;
    table.dayOfWeek=(int)dow;
  }
  | ( AT
      (
        "yearly"
      {
        table.set("H H H H *",getHashForTokens());
      }
      | "annually"
      {
        table.set("H H H H *",getHashForTokens());
      }
      | "monthly"
      {
        table.set("H H H * *",getHashForTokens());
      }
      | "weekly"
      {
        table.set("H H * * H",getHashForTokens());
      }
      | "daily"
      {
        table.set("H H * * *",getHashForTokens());
      }
      | "midnight"
      {
        table.set("H H(0-2) * * *",getHashForTokens());
      }
      | "hourly"
      {
        table.set("H * * * *",getHashForTokens());
      }
    )
  )
  ;

expr [int field]
returns [long bits=0]
throws ANTLRException
{
  long lhs,rhs=0;
}
  : lhs=term[field] ("," rhs=expr[field])?
  {
    bits = lhs|rhs;
  }
  ;

term [int field]
returns [long bits=0]
throws ANTLRException
{
  int d=NO_STEP,s,e,t;
}
  : (token "-")=> s=token "-" e=token ( "/" d=token )?
  {
    bits = doRange(s,e,d,field);
  }
  | t=token
  {
    rangeCheck(t,field);
    bits = 1L<<t;
  }
  | "*" ("/" d=token )?
  {
    bits = doRange(d,field);
  }
  | ("H" "(")=> "H" "(" s=token "-" e=token ")" ( "/" d=token )?
  {
    bits = doHash(s,e,d,field);
  }
  | "H" ( "/" d=token )?
  {
    bits = doHash(d,field);
  }
  ;

token
returns [int value=0]
  : t:TOKEN
  {
    value = Integer.parseInt(t.getText());
  }
  ;

class CrontabLexer extends Lexer;
options {
  k=2; // I'm sure there's a better way to do this than using lookahead. ANTLR sucks...
  defaultErrorHandler=false;
}

TOKEN
options {
  paraphrase="a number";
}
  : ('0'..'9')+
  ;

WS
options {
  paraphrase="space";
}
  : (' '|'\t')+
  ;

MINUS:  '-';
STAR: '*';
DIV:  '/';
OR:   ',';
AT:   '@';
H:    'H';
LPAREN: '(';
RPAREN: ')';

YEARLY: "yearly";
ANNUALLY: "annually";
MONTHLY: "monthly";
WEEKLY: "weekly";
DAILY: "daily";
MIDNIGHT: "midnight";
HOURLY: "hourly";
