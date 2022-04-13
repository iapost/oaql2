package oaql2;
import java_cup.runtime.*;
import java.io.StringReader;
import java.io.IOException;

%%

%class Lexer
%unicode
%cup
%line
%column
%caseless

%{
  StringBuffer queryHolder=new StringBuffer();
  StringBuffer string = new StringBuffer();

  private Symbol symbol(int type) {
    return new Symbol(type, yyline, yycolumn);
  }
  private Symbol symbol(int type, Object value) {
    return new Symbol(type, yyline, yycolumn, value);
  }
%}

Identifier = [A-Za-z][A-Za-z0-9\-_]*

NumLiteral = [-+]?[0-9]*\.?[0-9]+([eE][-+]?[0-9]+)?

%state STRING, INITIALSTRING, FROMPARSING, NORMALPARSING

%%

<YYINITIAL>{
  \"                          { queryHolder.append(yytext()); yybegin(INITIALSTRING); }
  [ \t\r\n\f]FROM[ \t\r\n\f]  { yybegin (FROMPARSING); queryHolder.append(" "); return symbol(sym.FROM); }
  <<EOF>>                     { yyclose(); yyreset(new StringReader(queryHolder.toString())); yybegin(NORMALPARSING); }
  [^]                         { queryHolder.append(yytext()); }
}

<INITIALSTRING> {
  \"                  { queryHolder.append(yytext()); yybegin(YYINITIAL); }
  \\\"|[^]            { queryHolder.append(yytext()); }
}

<FROMPARSING>{
  "AS"                { return symbol(sym.AS); }
  "JOIN"              { return symbol(sym.JOIN); }
  "ON"                { return symbol(sym.ON); }
  "WHERE"             { yybegin(YYINITIAL); queryHolder.append(yytext()); }
  "ORDER"             { yybegin(YYINITIAL); queryHolder.append(yytext()); }
  "."                 { return symbol(sym.DOT); }
  "="                 { return symbol(sym.EQ); }
  {Identifier}        { return symbol(sym.IDENTIFIER,yytext()); }
  " "|\t|\r|\n|\f     { /* ignore */ }
  <<EOF>>             { yyclose(); yyreset(new StringReader(queryHolder.toString())); yybegin(NORMALPARSING); }
}

<NORMALPARSING> {
  "("                 { return symbol(sym.LP); }
  ")"                 { return symbol(sym.RP); }
  ","                 { return symbol(sym.COMMA); }
  "."                 { return symbol(sym.DOT); }
  "*"                 { return symbol(sym.STAR); }
  "="                 { return symbol(sym.OPERATOR,"$eq"); }
  "<>"                { return symbol(sym.OPERATOR,"$ne"); }
  ">"                 { return symbol(sym.OPERATOR,"$gt"); }
  ">="                { return symbol(sym.OPERATOR,"$gte"); }
  "<"                 { return symbol(sym.OPERATOR,"$lt"); }
  "<="                { return symbol(sym.OPERATOR,"$lte"); }
  "SELECT"            { return symbol(sym.SELECT); }
  "DISTINCT"          { return symbol(sym.DISTINCT); }
  "AS"                { return symbol(sym.AS); }
  "WHERE"             { return symbol(sym.WHERE); }
  "AND"               { return symbol(sym.AND); }
  "OR"                { return symbol(sym.OR); }
  "XOR"               { return symbol(sym.XOR); }
  "NOT"               { return symbol(sym.NOT); }
  "IN"                { return symbol(sym.IN); }
  "IS"                { return symbol(sym.IS); }
  "NULL"              { return symbol(sym.NULL); }
  "BETWEEN"           { return symbol(sym.BETWEEN); }
  "ORDER"             { return symbol(sym.ORDER); }
  "BY"                { return symbol(sym.BY); }
  "ASC"               { return symbol(sym.ASCDESC,1); }
  "DESC"              { return symbol(sym.ASCDESC,-1); }
  "LIKE"              { return symbol(sym.LIKE); }
  "true"              { return symbol(sym.BOOL_VALUE,true); }
  "false"             { return symbol(sym.BOOL_VALUE,false); }

  {Identifier}                   { return symbol(sym.IDENTIFIER,yytext()); }

  {Identifier}\.{Identifier}      { return symbol(sym.FIELD,yytext()); }

  {NumLiteral}               { return symbol(sym.NUM_VALUE,Double.parseDouble(yytext())); }

  \"                             { string.setLength(0); yybegin(STRING); }

  " "|\t|\r|\n|\f                   { /* ignore */ }
}

<STRING> {
  \"                             { yybegin(NORMALPARSING); return symbol(sym.STRING_VALUE, string.toString()); }
  [^\n\r\t\"\\]+                 { string.append(yytext()); }
  \\t                            { string.append('\t'); }
  \\n                            { string.append('\n'); }

  \\r                            { string.append('\r'); }
  \\\"                           { string.append('\"'); }
  \\                             { string.append('\\'); }
}

[^]                              { throw new IOException("Illegal character <" + yytext()+">"); }