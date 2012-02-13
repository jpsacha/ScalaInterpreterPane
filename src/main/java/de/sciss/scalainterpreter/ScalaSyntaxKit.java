package de.sciss.scalainterpreter;

import jsyntaxpane.DefaultSyntaxKit;
import jsyntaxpane.Lexer;

// use our own lexer which is a corrected version
// of the original jsyntaxpane lexer
public class ScalaSyntaxKit extends DefaultSyntaxKit {
    public ScalaSyntaxKit() {
        super( new ScalaLexer() );
//System.out.println( "YES" );
    }

    public ScalaSyntaxKit( Lexer lexer ) {
        super( lexer );
//System.out.println( "YES" );
    }
}
