/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
lexer grammar DRLLexer;

import JavaLexer;

@members {
    public String normalizeString( String input ) {
        if( input != null && (input.length() == 2 || input.length() >= 4) ) {
            input = input.substring( 1, input.length() - 1 );
            input = input.replaceAll( "\'", "'" );
            input = input.replaceAll( "\"", "\\\"" );
            input = "\"" + input + "\"";
        }
        return input;
    }

    public boolean isRhsDrlEnd() {
        return new LexerHelper(_input).isRhsDrlEnd();
    }
}

/////////////////
// KEYWORDS
/////////////////

// These keywords are already declared in JavaLexer. They should not be overriden with different names, or else Vocabulary's literalName will be null.
// So no need to declare by DRLLexer
// PACKAGE : 'package';
// IMPORT : 'import';
// STATIC : 'static';
// EXTENDS : 'extends';
// SUPER : 'super';

// DRL keywords
DRL_UNIT : 'unit';
DRL_FUNCTION : 'function';
DRL_GLOBAL : 'global';
DRL_DECLARE : 'declare';
DRL_TRAIT : 'trait';
DRL_TYPE : 'type';
DRL_RULE : 'rule';
DRL_QUERY : 'query';
DRL_WHEN : 'when';
DRL_THEN : 'then' -> pushMode(RHS);
DRL_END : 'end';

DRL_AND : 'and';
DRL_OR : 'or';

DRL_EXISTS : 'exists';
DRL_NOT : 'not';
DRL_IN : 'in';
DRL_FROM : 'from';
DRL_COLLECT : 'collect';
DRL_ACCUMULATE : 'accumulate';
DRL_ACC : 'acc';
DRL_INIT : 'init';
DRL_ACTION : 'action';
DRL_REVERSE : 'reverse';
DRL_RESULT : 'result';
DRL_ENTRY_POINT : 'entry-point';
DRL_EVAL : 'eval';
DRL_FORALL : 'forall';
DRL_OVER : 'over';
DRL_GROUPBY : 'groupby';

// constraint operators
DRL_MATCHES : 'matches';
DRL_MEMBEROF : 'memberOf';
DRL_CONTAINS : 'contains';
DRL_EXCLUDES : 'excludes';
DRL_SOUNDSLIKE : 'soundslike';
DRL_STR : 'str';

// temporal operators
DRL_AFTER : 'after';
DRL_BEFORE : 'before';
DRL_COINCIDES : 'coincides';
DRL_DURING : 'during';
DRL_INCLUDES : 'includes';
DRL_FINISHES : 'finishes';
DRL_FINISHED_BY : 'finishedby';
DRL_MEETS : 'meets';
DRL_MET_BY : 'metby';
DRL_OVERLAPS : 'overlaps';
DRL_OVERLAPPED_BY : 'overlappedby';
DRL_STARTS : 'starts';
DRL_STARTED_BY : 'startedby';

DRL_WINDOW : 'window';

// attributes
DRL_ATTRIBUTES : 'attributes';
DRL_SALIENCE : 'salience';
DRL_ENABLED : 'enabled';
DRL_NO_LOOP : 'no-loop';
DRL_AUTO_FOCUS : 'auto-focus';
DRL_LOCK_ON_ACTIVE : 'lock-on-active';
DRL_REFRACT : 'refract';
DRL_DIRECT : 'direct';
DRL_AGENDA_GROUP : 'agenda-group';
DRL_ACTIVATION_GROUP : 'activation-group';
DRL_RULEFLOW_GROUP : 'ruleflow-group';
DRL_DATE_EFFECTIVE : 'date-effective';
DRL_DATE_EXPIRES : 'date-expires';
DRL_DIALECT : 'dialect';
DRL_CALENDARS : 'calendars';
DRL_TIMER : 'timer';
DRL_DURATION : 'duration';

DRL_CUSTOM_OPERATOR_PREFIX : '##' ;

/////////////////
// LEXER
/////////////////

TIME_INTERVAL
    : (('0'..'9')+ 'd') (('0'..'9')+ 'h')?(('0'..'9')+ 'm')?(('0'..'9')+ 's')?(('0'..'9')+ 'ms'?)?
    | (('0'..'9')+ 'h') (('0'..'9')+ 'm')?(('0'..'9')+ 's')?(('0'..'9')+ 'ms'?)?
    | (('0'..'9')+ 'm') (('0'..'9')+ 's')?(('0'..'9')+ 'ms'?)?
    | (('0'..'9')+ 's') (('0'..'9')+ 'ms'?)?
    | (('0'..'9')+ 'ms')
    ;

DRL_STRING_LITERAL
    :  ('"' ( DrlEscapeSequence | ~('\\'|'"') )* '"')
    |  ('\'' ( DrlEscapeSequence | ~('\\'|'\'') )* '\'') { setText( normalizeString( getText() ) ); }
    ;

DRL_BIG_DECIMAL_LITERAL
    :  ('0'..'9')+ [B]
    |  ('0'..'9')+ '.' ('0'..'9')+ [B]
    ;

DRL_BIG_INTEGER_LITERAL
    :  ('0'..'9')+ [I]
    ;

/////////////////
// SYMBOLS
/////////////////

HASH : '#';
DRL_UNIFY :	':=' ;
NULL_SAFE_DOT :	'!.' ;
QUESTION_DIV :	'?/' ;

MISC : '\'' | '\\' | '$' ;

/////////////////
// Fragment
/////////////////
fragment
DrlEscapeSequence
    :   '\\' ('b'|'B'|'t'|'n'|'f'|'r'|'"'|'\''|'\\'|'.'|'o'|
              'x'|'a'|'e'|'c'|'d'|'D'|'s'|'S'|'w'|'W'|'p'|'A'|
              'G'|'Z'|'z'|'Q'|'E'|'*'|'['|']'|'('|')'|'$'|'^'|
              '{'|'}'|'?'|'+'|'-'|'&'|'|')
    |   DrlUnicodeEscape
    |   DrlOctalEscape
    ;

fragment
DrlOctalEscape
    :   '\\' ('0'..'3') ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7')
    ;

fragment
DrlUnicodeEscape
    :   '\\' 'u' HexDigit HexDigit HexDigit HexDigit
    ;

mode RHS;
RHS_WS : [ \t\r\n\u000C]+ -> channel(HIDDEN);
RHS_COMMENT:            '/*' .*? '*/' ;
RHS_LINE_COMMENT:       '//' ~[\r\n]* ;

//DRL_RHS_END : 'end' [ \t]* SEMI? [ \t]* ('\n' | '\r\n' | EOF) { setText("end"); } -> popMode;
DRL_RHS_END : {isRhsDrlEnd()}? DRL_END -> popMode;

RHS_STRING_LITERAL
      // cannot reuse DRL_STRING_LITERAL because Actions are ignored in referenced rules
    : ('"' ( DrlEscapeSequence | ~('\\'|'"') )* '"')
    | ('\'' ( DrlEscapeSequence | ~('\\'|'\'') )* '\'') { setText( normalizeString( getText() ) ); }
    ;

RHS_NAMED_CONSEQUENCE_THEN : DRL_THEN LBRACK IDENTIFIER RBRACK ;

RHS_CHUNK
    : ~[ "'()[\]{},;\t\r\n\u000C]+ // ;}) could be a delimitter proceding 'end'. ()[]{},; are delimiters to match RHS_STRING_LITERAL
    | LPAREN
    | RPAREN
    | LBRACK
    | RBRACK
    | LBRACE
    | RBRACE
    | COMMA
    | SEMI
    ;
