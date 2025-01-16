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
parser grammar DRL6Expressions;

options {
    language = Java;
    tokenVocab = DRLLexer;
    superClass=DRLExpressions;
}

@header {
    import java.util.LinkedList;
    import org.drools.drl.parser.DroolsParserException;
    import org.drools.drl.parser.lang.DroolsEditorType;
    import org.drools.drl.parser.lang.DroolsParserExceptionFactory;
    import org.drools.drl.parser.lang.DroolsSentence;
    import org.drools.drl.parser.lang.DroolsSoftKeywords;
    import org.drools.drl.parser.lang.Location;

    import org.drools.drl.ast.dsl.AnnotatedDescrBuilder;
    import org.drools.drl.ast.dsl.AnnotationDescrBuilder;

    import org.drools.drl.ast.descr.AnnotatedBaseDescr;
    import org.drools.drl.ast.descr.AnnotationDescr;
    import org.drools.drl.ast.descr.AtomicExprDescr;
    import org.drools.drl.ast.descr.BaseDescr;
    import org.drools.drl.ast.descr.BindingDescr;
    import org.drools.drl.ast.descr.ConstraintConnectiveDescr;
    import org.drools.drl.ast.descr.RelationalExprDescr;
}

@members {
    private ParserHelper helper;

    public void setHelper( ParserHelper helper )              { this.helper = helper; }
    public ParserHelper getHelper()                           { return helper; }
    public boolean hasErrors()                                { return helper.hasErrors(); }
    public List<DroolsParserException> getErrors()            { return helper.getErrors(); }
    public List<String> getErrorMessages()                    { return helper.getErrorMessages(); }
    public void enableEditorInterface()                       {        helper.enableEditorInterface(); }
    public void disableEditorInterface()                      {        helper.disableEditorInterface(); }
    public LinkedList<DroolsSentence> getEditorInterface()    { return helper.getEditorInterface(); }
    public void reportError(RecognitionException ex)          {        helper.reportError( ex ); }
    public void emitErrorMessage(String msg)                  {}

    private boolean buildDescr;
    private int inMap = 0;
    private int ternOp = 0;
    private boolean hasBindings;
    public void setBuildDescr( boolean build ) { this.buildDescr = build; }
    public boolean isBuildDescr() { return this.buildDescr; }

    public void setLeftMostExpr( String value ) { helper.setLeftMostExpr( value ); }
    public String getLeftMostExpr() { return helper.getLeftMostExpr(); }

    public void setHasBindings( boolean value ) { this.hasBindings = value; }
    public boolean hasBindings() { return this.hasBindings; }

    public final BaseDescr conditionalOrExpressionDescr() throws RecognitionException {
        return conditionalOrExpression().result;
    }

    private boolean isNotEOF() {
        // TODO verify that we can omit the backtracking check
        /*if (state.backtracking != 0){
            return false;
        }*/
        if (_input.get( _input.index() - 1 ).getType() == DRLLexer.WS){
            return true;
        }
        if (_input.LA(-1) == DRLLexer.LPAREN){
            return true;
        }
        return _input.get( _input.index() ).getType() != DRLLexer.EOF;
    }

    private boolean notStartWithNewline() {
        int currentTokenIndex = _input.index(); // current position in input stream
        Token previousHiddenToken = _input.get(currentTokenIndex - 1);
        String previousHiddenTokenText = previousHiddenToken.getText();
        return !previousHiddenTokenText.contains("\n");
    }
}

// Alter code generation so catch-clauses get replace with
// this action.
@rulecatch {
catch (RecognitionException re) {
    throw re;
}
}

// --------------------------------------------------------
//                      GENERAL RULES
// --------------------------------------------------------
literal
    :	STRING_LITERAL  {	helper.emit($STRING_LITERAL, DroolsEditorType.STRING_CONST);	}
    |	DRL_STRING_LITERAL  {	helper.emit($DRL_STRING_LITERAL, DroolsEditorType.STRING_CONST);	}
    |	DECIMAL_LITERAL {	helper.emit($DECIMAL_LITERAL, DroolsEditorType.NUMERIC_CONST);	}
    |	DRL_BIG_INTEGER_LITERAL {	helper.emit($DRL_BIG_INTEGER_LITERAL, DroolsEditorType.NUMERIC_CONST);	}
    |	HEX_LITERAL     {	helper.emit($HEX_LITERAL, DroolsEditorType.NUMERIC_CONST);	}
    |	FLOAT_LITERAL   {	helper.emit($FLOAT_LITERAL, DroolsEditorType.NUMERIC_CONST);	}
    |	DRL_BIG_DECIMAL_LITERAL   {	helper.emit($DRL_BIG_DECIMAL_LITERAL, DroolsEditorType.NUMERIC_CONST);	}
    |	BOOL_LITERAL    {	helper.emit($BOOL_LITERAL, DroolsEditorType.BOOLEAN_CONST);	}
    |	NULL_LITERAL    {	helper.emit($NULL_LITERAL, DroolsEditorType.NULL_CONST);	}
    |   TIME_INTERVAL {	helper.emit($TIME_INTERVAL, DroolsEditorType.NULL_CONST); }
    |   MUL           { helper.emit($MUL, DroolsEditorType.NUMERIC_CONST); } // this means "infinity" in Drools
    ;

operator returns [boolean negated, String opr]
@init{ if ( isNotEOF() ) helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_OPERATOR ); helper.setHasOperator( true ); }
// TODO verify that we can omit the backtracking check
@after{ if( /*state.backtracking == 0 &&*/ _input.LA( 1 ) != DRLLexer.EOF) { helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_ARGUMENT ); } }
  : x=TILDE?
    ( op=EQUAL         { $negated = false; $opr=($x != null ? $x.text : "")+$op.text; helper.emit($op, DroolsEditorType.SYMBOL); }
    | op=NOTEQUAL      { $negated = false; $opr=($x != null ? $x.text : "")+$op.text; helper.emit($op, DroolsEditorType.SYMBOL); }
    | rop=relationalOp { $negated = $rop.negated; $opr=($x != null ? $x.text : "")+$rop.opr; }
    )
    ;



relationalOp returns [boolean negated, String opr, java.util.List<String> params]
@init{ if ( isNotEOF() ) helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_OPERATOR ); helper.setHasOperator( true ); }
// TODO verify that we can omit the backtracking check
@after{ if( /*state.backtracking == 0 &&*/ _input.LA( 1 ) != DRLLexer.EOF) { helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_ARGUMENT ); } }
  : ( op=LE              { $negated = false; $opr=$op.text; $params = null; helper.emit($op, DroolsEditorType.SYMBOL);}
    | op=GE              { $negated = false; $opr=$op.text; $params = null; helper.emit($op, DroolsEditorType.SYMBOL);}
    | op=LT              { $negated = false; $opr=$op.text; $params = null; helper.emit($op, DroolsEditorType.SYMBOL);}
    | op=GT              { $negated = false; $opr=$op.text; $params = null; helper.emit($op, DroolsEditorType.SYMBOL);}
    | xop=complexOp      { $negated = false; $opr=$op.text; $params = null; helper.emit($op, DroolsEditorType.SYMBOL);}
    | not_key nop=neg_operator_key { $negated = true; $opr=$nop.text;}
    | cop=operator_key  { $negated = false; $opr=$cop.text;}
    )
    ;

complexOp returns [String opr]
    : t=TILDE e=ASSIGN   { $opr=$t.text+$e.text; }
    ;

typeList
    :	type (COMMA type)*
    ;

type
    : 	tm=typeMatch
    ;

typeMatch
    : primitiveType (LBRACK RBRACK)*
    |	drlIdentifier (typeArguments)? (DOT drlIdentifier (typeArguments)? )* (LBRACK RBRACK)*
    ;

typeArguments
    :	LT typeArgument (COMMA typeArgument)* GT
    ;

typeArgument
    :	type
    |	QUESTION ((extends_key | super_key) type)?
    ;

// matches any identifiers including acceptable java keywords (defined in JavaParser.g4) and drl keywords
drlIdentifier returns [Token token]
    : drlKeywords
    | IDENTIFIER
    // java keywords
    | ABSTRACT
    | ASSERT
    | BOOLEAN
    | BREAK
    | BYTE
    | CASE
    | CATCH
    | CHAR
    | CLASS
    | CONST
    | CONTINUE
    | DEFAULT
    | DO
    | DOUBLE
    | ELSE
    | ENUM
    | EXTENDS
    | FINAL
    | FINALLY
    | FLOAT
    | FOR
    | IF
    | GOTO
    | IMPLEMENTS
    | IMPORT
    | INSTANCEOF
    | INT
    | INTERFACE
    | LONG
    | NATIVE
//    | NEW     // avoid ambiguity with 'new_key creator' and 'drlIdentifier' in 'primary'
    | PACKAGE
    | PRIVATE
    | PROTECTED
    | PUBLIC
    | RETURN
    | SHORT
    | STATIC
    | STRICTFP
    | SUPER
    | SWITCH
    | SYNCHRONIZED
    | THIS
    | THROW
    | THROWS
    | TRANSIENT
    | TRY
    | VOID
    | VOLATILE
    | WHILE
    // Module related keywords
    | MODULE
    | OPEN
    | REQUIRES
    | EXPORTS
    | OPENS
    | TO
    | USES
    | PROVIDES
    | WITH
    | TRANSITIVE
    // other java keywords
    | VAR
    | YIELD
    | RECORD
    | SEALED
    | PERMITS
    | NON_SEALED
    ;

// matches any drl keywords
drlKeywords returns [Token token]
    : builtInOperator
    | DRL_UNIT
    | DRL_FUNCTION
    | DRL_GLOBAL
    | DRL_DECLARE
    | DRL_TRAIT
    | DRL_TYPE
    | DRL_RULE
    | DRL_QUERY
    | DRL_WHEN
    | DRL_THEN
    | DRL_END
    | DRL_AND
    | DRL_OR
    | DRL_EXISTS
    | DRL_NOT
    | DRL_IN
    | DRL_FROM
    | DRL_COLLECT
    | DRL_ACCUMULATE
    | DRL_ACC
    | DRL_INIT
    | DRL_ACTION
    | DRL_REVERSE
    | DRL_RESULT
    | DRL_ENTRY_POINT
    | DRL_EVAL
    | DRL_FORALL
    | DRL_OVER
    | DRL_ATTRIBUTES
    | DRL_SALIENCE
    | DRL_ENABLED
    | DRL_NO_LOOP
    | DRL_AUTO_FOCUS
    | DRL_LOCK_ON_ACTIVE
    | DRL_REFRACT
    | DRL_DIRECT
    | DRL_AGENDA_GROUP
    | DRL_ACTIVATION_GROUP
    | DRL_RULEFLOW_GROUP
    | DRL_DATE_EFFECTIVE
    | DRL_DATE_EXPIRES
    | DRL_DIALECT
    | DRL_CALENDARS
    | DRL_TIMER
    | DRL_DURATION
    | DRL_WINDOW
    ;

builtInOperator returns[Token token]
    : DRL_CONTAINS
    | DRL_EXCLUDES
    | DRL_MATCHES
    | DRL_MEMBEROF
    | DRL_SOUNDSLIKE
    | DRL_AFTER
    | DRL_BEFORE
    | DRL_COINCIDES
    | DRL_DURING
    | DRL_FINISHED_BY
    | DRL_FINISHES
    | DRL_INCLUDES
    | DRL_MEETS
    | DRL_MET_BY
    | DRL_OVERLAPPED_BY
    | DRL_OVERLAPS
    | DRL_STARTED_BY
    | DRL_STARTS
    | DRL_STR
    ;

// --------------------------------------------------------
//                      EXPRESSIONS
// --------------------------------------------------------
// the following dymmy rule is to force the AT symbol to be
// included in the follow set of the expression on the DFAs
dummy
    :	expression ( AT | SEMI | EOF | IDENTIFIER | RPAREN ) ;

dummy2
    :  relationalExpression EOF;

// top level entry point for arbitrary expression parsing
expression returns [BaseDescr result]
    :	left=conditionalExpression { if( buildDescr  ) { $result = $left.result; } }
        (op=assignmentOperator right=expression)?
    ;

conditionalExpression returns [BaseDescr result]
    :   left=conditionalOrExpression { if( buildDescr  ) { $result = $left.result; } }
        ternaryExpression?
    ;

ternaryExpression
@init{ ternOp++; }
    :	QUESTION ts=expression COLON fs=expression
    ;
finally { ternOp--; }


fullAnnotation [AnnotatedDescrBuilder inDescrBuilder] returns [AnnotationDescr result]
@init{ String n = ""; AnnotationDescrBuilder annoBuilder = null; }
  : AT name=drlIdentifier { n = $name.text; } ( DOT x=drlIdentifier { n += "." + $x.text; } )*
        { if( buildDescr ) {
                if ( inDescrBuilder == null ) {
                    $result = new AnnotationDescr( n );
                } else {
                    annoBuilder = inDescrBuilder instanceof AnnotationDescrBuilder ?
                        ((AnnotationDescrBuilder) inDescrBuilder).newAnnotation( n ) : inDescrBuilder.newAnnotation( n );
                    $result = (AnnotationDescr) annoBuilder.getDescr();
                }
            }
        }
    annotationArgs[$result, annoBuilder]
  ;

annotationArgs [AnnotationDescr descr, AnnotatedDescrBuilder inDescrBuilder]
  : LPAREN
    (
       annotationElementValuePairs[descr, inDescrBuilder]
       | value=annotationValue[inDescrBuilder] { if ( buildDescr ) { $descr.setValue( $value.result ); } }
    )?
    RPAREN
  ;

annotationElementValuePairs [AnnotationDescr descr, AnnotatedDescrBuilder inDescrBuilder]
  : annotationElementValuePair[descr, inDescrBuilder] ( COMMA annotationElementValuePair[descr, inDescrBuilder] )*
  ;

annotationElementValuePair [AnnotationDescr descr, AnnotatedDescrBuilder inDescrBuilder]
  : key=drlIdentifier ASSIGN val=annotationValue[inDescrBuilder] { if ( buildDescr ) { $descr.setKeyValue( $key.text, $val.result ); } }
  ;

annotationValue[AnnotatedDescrBuilder inDescrBuilder] returns [Object result]
  : exp=expression { if ( buildDescr ) $result = $exp.text; }
    | annos=annotationArray[inDescrBuilder] { if ( buildDescr ) $result = $annos.result.toArray(); }
    | anno=fullAnnotation[inDescrBuilder] { if ( buildDescr ) $result = $anno.result; }
  ;

annotationArray[AnnotatedDescrBuilder inDescrBuilder] returns [java.util.List result]
@init { $result = new java.util.ArrayList();}
  :  LBRACE ( anno=annotationValue[inDescrBuilder] { $result.add( $anno.result ); }
                ( COMMA anno=annotationValue[inDescrBuilder] { $result.add( $anno.result ); } )* )?
     RBRACE
  ;



conditionalOrExpression returns [BaseDescr result]
  : left=conditionalAndExpression  { if( buildDescr ) { $result = $left.result; } }
  ( OR
        {  if ( isNotEOF() ) helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_OPERATOR );  }
        args=fullAnnotation[null]? right=conditionalAndExpression
         { if( buildDescr  ) {
               ConstraintConnectiveDescr descr = ConstraintConnectiveDescr.newOr();
               descr.addOrMerge( $result );
               descr.addOrMerge( $right.result );
               if ( $ctx.args != null ) { descr.addAnnotation( $args.result ); }
               $result = descr;
           }
         }
  )*
  ;

conditionalAndExpression returns [BaseDescr result]
  : left=inclusiveOrExpression { if( buildDescr  ) { $result = $left.result; } }
  ( AND
         { if ( isNotEOF() ) helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_OPERATOR ); }
        args=fullAnnotation[null]? right=inclusiveOrExpression
         { if( buildDescr  ) {
               ConstraintConnectiveDescr descr = ConstraintConnectiveDescr.newAnd();
               descr.addOrMerge( $result );
               descr.addOrMerge( $right.result );
               if ( $ctx.args != null ) { descr.addAnnotation( $args.result ); }
               $result = descr;
           }
         }
  )*
  ;

inclusiveOrExpression returns [BaseDescr result]
  : left=exclusiveOrExpression { if( buildDescr  ) { $result = $left.result; } }
  ( BITOR right=exclusiveOrExpression
         { if( buildDescr  ) {
               ConstraintConnectiveDescr descr = ConstraintConnectiveDescr.newIncOr();
               descr.addOrMerge( $result );
               descr.addOrMerge( $right.result );
               $result = descr;
           }
         }
  )*
  ;

exclusiveOrExpression returns [BaseDescr result]
  : left=andExpression { if( buildDescr  ) { $result = $left.result; } }
  ( CARET right=andExpression
         { if( buildDescr  ) {
               ConstraintConnectiveDescr descr = ConstraintConnectiveDescr.newXor();
               descr.addOrMerge( $result );
               descr.addOrMerge( $right.result );
               $result = descr;
           }
         }
  )*
  ;

andExpression returns [BaseDescr result]
  : left=equalityExpression { if( buildDescr  ) { $result = $left.result; } }
  ( BITAND right=equalityExpression
         { if( buildDescr  ) {
               ConstraintConnectiveDescr descr = ConstraintConnectiveDescr.newIncAnd();
               descr.addOrMerge( $result );
               descr.addOrMerge( $right.result );
               $result = descr;
           }
         }
  )*
  ;

equalityExpression returns [BaseDescr result]
  : left=instanceOfExpression { if( buildDescr  ) { $result = $left.result; } }
  ( ( op=EQUAL | op=NOTEQUAL )
    {  helper.setHasOperator( true );
       if( _input.LA( 1 ) != DRLLexer.EOF ) helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_ARGUMENT ); }
    right=instanceOfExpression
         { if( buildDescr  ) {
               $result = new RelationalExprDescr( $op.text, false, null, $left.result, $right.result );
           }
         }
  )*
  ;

instanceOfExpression returns [BaseDescr result]
  : left=inExpression { if( buildDescr  ) { $result = $left.result; } }
  ( op=instanceof_key
    {  helper.setHasOperator( true );
       if( _input.LA( 1 ) != DRLLexer.EOF ) helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_ARGUMENT ); }
    right=type
         { if( buildDescr  ) {
               $result = new RelationalExprDescr( $op.text, false, null, $left.result, new AtomicExprDescr($right.text) );
           }
         }
  )?
  ;

inExpression returns [BaseDescr result]
@init { ConstraintConnectiveDescr descr = null; BaseDescr leftDescr = null; BindingDescr binding = null; }
@after { if( binding != null && descr != null ) descr.addOrMerge( binding ); }
  : left=relationalExpression
    { if( buildDescr  ) { $result = $left.result; }
      if( $left.result instanceof BindingDescr ) {
          binding = (BindingDescr)$left.result;
          leftDescr = new AtomicExprDescr( binding.getExpression() );
      } else {
          leftDescr = $left.result;
      }
    }
    (not_key in=in_key LPAREN
        {   helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_ARGUMENT ); }
        e1=expression
        {   descr = ConstraintConnectiveDescr.newAnd();
            RelationalExprDescr rel1 = new RelationalExprDescr( "!=", false, null, leftDescr, $e1.result );
            descr.addOrMerge( rel1 );
            $result = descr;
        }
      (COMMA e2=expression
        {   RelationalExprDescr rel2 = new RelationalExprDescr( "!=", false, null, leftDescr, $e2.result );
            descr.addOrMerge( rel2 );
        }
      )* RPAREN
    { helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_END ); }
    | in=in_key LPAREN
        {   helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_ARGUMENT ); }
        e1=expression
        {   descr = ConstraintConnectiveDescr.newOr();
            RelationalExprDescr rel1 = new RelationalExprDescr( "==", false, null, leftDescr, $e1.result );
            descr.addOrMerge( rel1 );
            $result = descr;
        }
      (COMMA e2=expression
        {   RelationalExprDescr rel2 = new RelationalExprDescr( "==", false, null, leftDescr, $e2.result );
            descr.addOrMerge( rel2 );
        }
      )* RPAREN
    { helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_END ); }
    )?
  ;

relationalExpression returns [BaseDescr result]
locals [ BaseDescr lsd ]
// TODO access lsd directly instead of through dynamic context here
@init { $relationalExpression::lsd = null; }
  : left=shiftExpression
    { if( buildDescr  ) {
          if ( $left.result == null ) {
            $result = new AtomicExprDescr( $left.text );
          } else if ( $left.result instanceof AtomicExprDescr ) {
            if ( $left.text.equals(((AtomicExprDescr)$left.result).getExpression()) ) {
              $result = $left.result;
            } else {
              $result = new AtomicExprDescr( $left.text ) ;
            }
          } else if ( $left.result instanceof BindingDescr ) {
              if ( $left.text.equals(((BindingDescr)$left.result).getExpression()) ) {
                $result = $left.result;
              } else {
                BindingDescr bind = (BindingDescr) $left.result;
                int offset = bind.isUnification() ? 2 : 1;
                String fullExpression = $left.text.substring( $left.text.indexOf( ":" ) + offset ).trim();
                $result = new BindingDescr( bind.getVariable(), bind.getExpression(), fullExpression, bind.isUnification() );
              }
          } else {
              $result = $left.result;
          }
          // TODO access lsd directly instead of through dynamic context here
          $relationalExpression::lsd = $result;
      }
    }
  ( right=orRestriction
         { if( buildDescr  ) {
               $result = $right.result;
               // TODO access lsd directly instead of through dynamic context here
               $relationalExpression::lsd = $result;
           }
         }
  )*
  ;

orRestriction returns [BaseDescr result]
  : left=andRestriction { if( buildDescr  ) { $result = $left.result; } }
    ( lop=OR args=fullAnnotation[null]? right=andRestriction
         { if( buildDescr ) {
               ConstraintConnectiveDescr descr = ConstraintConnectiveDescr.newOr();
               descr.addOrMerge( $result );
               descr.addOrMerge( $right.result );
               if ( $ctx.args != null ) { descr.addAnnotation( $args.result ); }
               $result = descr;
           }
         }
   )*? EOF?
  ;

andRestriction returns [BaseDescr result]
  : left=singleRestriction { if( buildDescr  ) { $result = $left.result; } }
  ( lop=AND
  	    { if ( isNotEOF() ) helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_OPERATOR ); }
        args=fullAnnotation[null]?right=singleRestriction
         { if( buildDescr  ) {
               ConstraintConnectiveDescr descr = ConstraintConnectiveDescr.newAnd();
               descr.addOrMerge( $result );
               descr.addOrMerge( $right.result );
               if ( $ctx.args != null ) { descr.addAnnotation( $args.result ); }
               $result = descr;
           }
         }
  )*?
  ;

singleRestriction returns [BaseDescr result]
  :  op=operator
         { helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_ARGUMENT ); }
     ( sa=squareArguments value=shiftExpression
       | value=shiftExpression
     )
         { if( buildDescr  ) {
               BaseDescr descr = ( $value.result != null &&
                                 ( (!($value.result instanceof AtomicExprDescr)) ||
                                   ($value.text.equals(((AtomicExprDescr)$value.result).getExpression())) )) ?
		                    $value.result :
		                    new AtomicExprDescr( $value.text ) ;
               $result = new RelationalExprDescr( $op.opr, $op.negated, $ctx.sa != null ? $sa.args : null, $relationalExpression::lsd, descr );
	       if( $relationalExpression::lsd instanceof BindingDescr ) {
	           $relationalExpression::lsd = new AtomicExprDescr( ((BindingDescr)$relationalExpression::lsd).getExpression() );
	       }
           }
           helper.emit( Location.LOCATION_LHS_INSIDE_CONDITION_END );
         }
  |  LPAREN or=orRestriction RPAREN  { $result = $or.result; }
  ;



shiftExpression returns [BaseDescr result]
  : left=additiveExpression { if( buildDescr  ) { $result = $left.result; } }
    ( shiftOp additiveExpression )*
  ;

shiftOp
    :	( LT LT
        | GT GT GT
        | GT GT  )
    ;

additiveExpression returns [BaseDescr result]
    :   left=multiplicativeExpression { if( buildDescr  ) { $result = $left.result; } }
        ( (ADD | SUB) multiplicativeExpression )*
    ;

multiplicativeExpression returns [BaseDescr result]
    :   left=unaryExpression { if( buildDescr  ) { $result = $left.result; } }
      ( ( MUL | DIV | MOD ) unaryExpression )*
    ;

unaryExpression returns [BaseDescr result]
    :   ADD ue=unaryExpression
        { if( buildDescr ) {
            $result = $ue.result;
            if( $result instanceof AtomicExprDescr ) {
                ((AtomicExprDescr)$result).setExpression( "+" + ((AtomicExprDescr)$result).getExpression() );
            }
        } }
    |	SUB ue=unaryExpression
        { if( buildDescr ) {
            $result = $ue.result;
            if( $result instanceof AtomicExprDescr ) {
                ((AtomicExprDescr)$result).setExpression( "-" + ((AtomicExprDescr)$result).getExpression() );
            }
        } }
    |   INC primary
    |   DEC primary
    |   left=unaryExpressionNotPlusMinus { if( buildDescr ) { $result = $left.result; } }
    ;

unaryExpressionNotPlusMinus returns [BaseDescr result]
@init { boolean isLeft = false; BindingDescr bind = null;}
    :   TILDE unaryExpression
    | 	BANG ue=unaryExpression
        {
            if( buildDescr && $ue.result != null ) {
                $result = $ue.result.negate();
            }
        }
    |   castExpression
    |   backReferenceExpression
    |   { isLeft = helper.getLeftMostExpr() == null;}
        ( ({inMap == 0 && ternOp == 0 && _input.LA(2) == DRLLexer.COLON}? (var=drlIdentifier COLON
                { hasBindings = true; helper.emit($var.token, DroolsEditorType.IDENTIFIER_VARIABLE); helper.emit($COLON, DroolsEditorType.SYMBOL); if( buildDescr ) { bind = new BindingDescr($var.text, null, false); helper.setStart( bind, $var.token ); } } ))
        | ({inMap == 0 && ternOp == 0 && _input.LA(2) == DRLLexer.DRL_UNIFY}? (var=drlIdentifier DRL_UNIFY
                { hasBindings = true; helper.emit($var.token, DroolsEditorType.IDENTIFIER_VARIABLE); helper.emit($DRL_UNIFY, DroolsEditorType.SYMBOL); if( buildDescr ) { bind = new BindingDescr($var.text, null, true); helper.setStart( bind, $var.token ); } } ))
        )?

        ( left2=xpathPrimary { if( buildDescr ) { $result = $left2.result; } }
          | left1=primary { if( buildDescr ) { $result = $left1.result; } }
        )

        (selector)*
        {
            if( buildDescr ) {
                String expr = $text;
                if( isLeft ) {
                    helper.setLeftMostExpr( expr );
                }
                if( bind != null ) {
                    if( bind.isUnification() ) {
                        expr = expr.substring( expr.indexOf( ":=" ) + 2 ).trim();
                    } else {
                        expr = expr.substring( expr.indexOf( ":" ) + 1 ).trim();
                    }
                    bind.setExpressionAndBindingField( expr );
                    helper.setEnd( bind );
                    $result = bind;
                }
            }
        }
        ((INC|DEC))?
    ;

castExpression
    :  LPAREN primitiveType RPAREN expr=unaryExpression
    |  LPAREN type RPAREN unaryExpressionNotPlusMinus
    ;

backReferenceExpression
    :  (DOT DOT DIV)+ unaryExpressionNotPlusMinus
    ;

primitiveType
    :   boolean_key
    |	char_key
    |	byte_key
    |	short_key
    |	int_key
    |	long_key
    |	float_key
    |	double_key
    ;

xpathSeparator
    :   DIV
    |	QUESTION_DIV
    ;

xpathPrimary returns [BaseDescr result]
    : xpathChunk ({notStartWithNewline()}? xpathChunk)*
    ;

xpathChunk returns [BaseDescr result]
    : xpathSeparator drlIdentifier (DOT drlIdentifier)* (HASH drlIdentifier)? (LBRACK xpathExpressionList RBRACK)?
    ;

xpathExpressionList returns [java.util.List<String> exprs]
@init { $exprs = new java.util.ArrayList<String>();}
  :   f=expression { $exprs.add( $f.text ); }
      (COMMA s=expression { $exprs.add( $s.text ); })*
  ;

primary returns [BaseDescr result]
    :	expr=parExpression {  if( buildDescr  ) { $result = $expr.result; }  }
    |   nonWildcardTypeArguments (explicitGenericInvocationSuffix | this_key arguments)
    |   literal { if( buildDescr  ) { $result = new AtomicExprDescr( $literal.text, true ); }  }
    |   super_key superSuffix
    |   new_key creator
    |   primitiveType (LBRACK RBRACK)* DOT class_key
    //|   void_key DOT class_key
    |   inlineMapExpression
    |   inlineListExpression
    |   i1=drlIdentifier { helper.emit($i1.token, DroolsEditorType.IDENTIFIER); }
        (
            ( d=DOT i2=drlIdentifier { helper.emit($d, DroolsEditorType.SYMBOL); helper.emit($i2.token, DroolsEditorType.IDENTIFIER); } )
            |
            ( d=(DOT|NULL_SAFE_DOT) LPAREN { helper.emit($d, DroolsEditorType.SYMBOL); helper.emit($LPAREN, DroolsEditorType.SYMBOL); }
                                    expression (COMMA { helper.emit($COMMA, DroolsEditorType.SYMBOL); } expression)*
                                    RPAREN { helper.emit($RPAREN, DroolsEditorType.SYMBOL); }
            )
            |
            ( h=HASH i2=drlIdentifier { helper.emit($h, DroolsEditorType.SYMBOL); helper.emit($i2.token, DroolsEditorType.IDENTIFIER); } )
            |
            ( n=NULL_SAFE_DOT i2=drlIdentifier { helper.emit($n, DroolsEditorType.SYMBOL); helper.emit($i2.token, DroolsEditorType.IDENTIFIER); } )
        )* (identifierSuffix)?
    ;

inlineListExpression
    :   LBRACK expressionList? RBRACK
    ;

inlineMapExpression
@init{ inMap++; }
    :	LBRACK mapExpressionList RBRACK
    ;
finally { inMap--; }

mapExpressionList
    :	mapEntry (COMMA mapEntry)*
    ;

mapEntry
    :	expression COLON expression
    ;

parExpression returns [BaseDescr result]
    :	LPAREN expr=expression RPAREN
        {  if( buildDescr  ) {
               $result = $expr.result;
               if( $result instanceof AtomicExprDescr ) {
                   ((AtomicExprDescr)$result).setExpression("(" +((AtomicExprDescr)$result).getExpression() + ")" );
               }
           }
        }
    ;

identifierSuffix
    :	(LBRACK { helper.emit($LBRACK, DroolsEditorType.SYMBOL); }
                                     RBRACK { helper.emit($RBRACK, DroolsEditorType.SYMBOL); } )+
                                     DOT { helper.emit($DOT, DroolsEditorType.SYMBOL); } class_key
    |	(LBRACK { helper.emit($LBRACK, DroolsEditorType.SYMBOL); }
                          expression
                          RBRACK { helper.emit($RBRACK, DroolsEditorType.SYMBOL); } )+ // can also be matched by selector, but do here
    |   arguments
    |   DOT class_key
//    |   DOT explicitGenericInvocation
//    |   DOT this_key
//    |   DOT super_key arguments
//    |   DOT new_key (nonWildcardTypeArguments)? innerCreator
    ;

creator
    :	nonWildcardTypeArguments? createdName
        (arrayCreatorRest | classCreatorRestExpr)
    ;

createdName
    :	drlIdentifier typeArguments?
        ( DOT drlIdentifier typeArguments?)*
        |	primitiveType
    ;

// Old parser cannot parse innerCreator with selector expression (outer.new InnerClass() != null) TODO: Delete this after investigation
innerCreator
    :	{!(helper.validateIdentifierKey(DroolsSoftKeywords.INSTANCEOF))}? drlIdentifier classCreatorRestExpr
    ;

arrayCreatorRest
    :   LBRACK
    (   RBRACK (LBRACK RBRACK)* arrayInitializer
        |   expression RBRACK ({!helper.validateLT(2,"]")}? LBRACK expression RBRACK)* (LBRACK RBRACK)*
        )
    ;

variableInitializer
    :	arrayInitializer
        |   expression
    ;

arrayInitializer
    :	LBRACE (variableInitializer (COMMA variableInitializer)* (COMMA)? )? RBRACE
    ;

classCreatorRestExpr // do not overwrite JavaParser.g4 classCreatorRest
    :	arguments //classBody?		//sotty:  restored classBody to allow for inline, anonymous classes
    ;

explicitGenericInvocation
    :	nonWildcardTypeArguments arguments
    ;

nonWildcardTypeArguments
    :	LT typeList GT
    ;

explicitGenericInvocationSuffix
    :	super_key superSuffix
    |   	drlIdentifier arguments
    ;

selector
    :   DOT { helper.emit($DOT, DroolsEditorType.SYMBOL); } super_key superSuffix
    |   DOT { helper.emit($DOT, DroolsEditorType.SYMBOL); } new_key (nonWildcardTypeArguments)? innerCreator
    |   DOT { helper.emit($DOT, DroolsEditorType.SYMBOL); }
                  id=drlIdentifier { helper.emit($id.token, DroolsEditorType.IDENTIFIER); }
                  (arguments)?
    |   NULL_SAFE_DOT { helper.emit($NULL_SAFE_DOT, DroolsEditorType.SYMBOL); }
                  id=drlIdentifier { helper.emit($id.token, DroolsEditorType.IDENTIFIER); }
                  (arguments)?
    //|   DOT this_key
    |   LBRACK { helper.emit($LBRACK, DroolsEditorType.SYMBOL); }
                       expression
                       RBRACK { helper.emit($RBRACK, DroolsEditorType.SYMBOL); }
    ;

superSuffix
    :	arguments
    |   	DOT drlIdentifier (arguments)?
    ;

squareArguments returns [java.util.List<String> args]
    : LBRACK (el=expressionList { $args = $el.exprs; })? RBRACK
    ;

arguments
    :	LPAREN { helper.emit($LPAREN, DroolsEditorType.SYMBOL); }
        expressionList?
        RPAREN { helper.emit($RPAREN, DroolsEditorType.SYMBOL); }
    ;

expressionList returns [java.util.List<String> exprs]
@init { $exprs = new java.util.ArrayList<String>();}
  :   f=expression { $exprs.add( $f.text ); }
      (COMMA s=expression { $exprs.add( $s.text ); })*
  ;

assignmentOperator
    :   ASSIGN
  |   ADD_ASSIGN
  |   SUB_ASSIGN
  |   MUL_ASSIGN
  |   DIV_ASSIGN
  |   AND_ASSIGN
  |   OR_ASSIGN
  |   XOR_ASSIGN
  |   MOD_ASSIGN
  |   LT LT ASSIGN
  |   GT GT GT ASSIGN
  |   GT GT ASSIGN
    ;

// --------------------------------------------------------
//                      KEYWORDS
// --------------------------------------------------------
extends_key
    : id=EXTENDS { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

super_key
    : id=SUPER { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

instanceof_key
    : id=INSTANCEOF { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

boolean_key
    : id=BOOLEAN { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

char_key
    : id=CHAR { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

byte_key
    : id=BYTE { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

short_key
    : id=SHORT { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

int_key
    : id=INT { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

float_key
    : id=FLOAT { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

long_key
    : id=LONG { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

double_key
    : id=DOUBLE { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

void_key
    : id=VOID { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

this_key
    : id=THIS { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

class_key
    : id=CLASS  { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

new_key
    : id=NEW { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

not_key
    : id=DRL_NOT { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

in_key
    : id=DRL_IN { helper.emit($id, DroolsEditorType.KEYWORD); }
    ;

operator_key
  :      DRL_CUSTOM_OPERATOR_PREFIX {(helper.isPluggableEvaluator(false))}? id=IDENTIFIER { helper.emit($id, DroolsEditorType.KEYWORD); }
  |      op=builtInOperator { helper.emit($op.token, DroolsEditorType.KEYWORD); }
  ;

neg_operator_key
  :      DRL_CUSTOM_OPERATOR_PREFIX {(helper.isPluggableEvaluator(true))}? id=IDENTIFIER { helper.emit($id, DroolsEditorType.KEYWORD); }
  |      op=builtInOperator { helper.emit($op.token, DroolsEditorType.KEYWORD); }
  ;
