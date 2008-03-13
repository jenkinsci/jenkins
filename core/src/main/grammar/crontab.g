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
        table.set("0 0 1 1 *");
      }
      | "annually"
      {
        table.set("0 0 1 1 *");
      }
      | "monthly"
      {
        table.set("0 0 1 * *");
      }
      | "weekly"
      {
        table.set("0 0 * * 0");
      }
      | "daily"
      {
        table.set("0 0 * * *");
      }
      | "midnight"
      {
        table.set("0 0 * * *");
      }
      | "hourly"
      {
        table.set("0 * * * *");
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
  int d=1,s,e,t;
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

YEARLY: "yearly";
ANNUALLY: "annually";
MONTHLY: "monthly";
WEEKLY: "weekly";
DAILY: "daily";
MIDNIGHT: "midnight";
HOURLY: "hourly";
