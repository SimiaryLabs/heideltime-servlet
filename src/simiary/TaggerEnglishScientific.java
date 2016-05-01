package simiary;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.unihd.dbs.heideltime.standalone.DocumentType;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;

/**
 * Servlet implementation class TaggerEnglishScientific
 */
@WebServlet("/TaggerEnglishScientific")
public class TaggerEnglishScientific extends TaggerEnglishNarratives implements Servlet {
	private static final long serialVersionUID = 1L;
    
    public void init(ServletConfig config) throws ServletException {
      super.init(config);
      heidelTimeInitialize(Language.ENGLISH, DocumentType.SCIENTIFIC);
    }

}
