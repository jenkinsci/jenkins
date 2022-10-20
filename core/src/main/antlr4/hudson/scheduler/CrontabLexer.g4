lexer grammar CrontabLexer;

TOKEN : ('0'..'9')+
   ;

WS : (' '|'\t')+
   ;

MINUS:  '-';
STAR: '*';
DIV:  '/';
OR:   ',';
AT:   '@';
H:    'H';
LPAREN: '(';
RPAREN: ')';
COMMA: ',';

YEARLY: 'yearly';
ANNUALLY: 'annually';
MONTHLY: 'monthly';
WEEKLY: 'weekly';
DAILY: 'daily';
MIDNIGHT: 'midnight';
HOURLY: 'hourly';
