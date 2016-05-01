package simiary;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.uima.UIMAFramework;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.XMLInputSource;
import org.json.JSONObject;
import org.json.XML;

import de.unihd.dbs.heideltime.standalone.Config;
import de.unihd.dbs.heideltime.standalone.DocumentType;
import de.unihd.dbs.heideltime.standalone.OutputType;
import de.unihd.dbs.heideltime.standalone.components.JCasFactory;
import de.unihd.dbs.heideltime.standalone.components.PartOfSpeechTagger;
import de.unihd.dbs.heideltime.standalone.components.ResultFormatter;
import de.unihd.dbs.heideltime.standalone.components.impl.IntervalTaggerWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.JCasFactoryImpl;
import de.unihd.dbs.heideltime.standalone.components.impl.StanfordPOSTaggerWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.TimeMLResultFormatter;
import de.unihd.dbs.heideltime.standalone.components.impl.UimaContextImpl;
import de.unihd.dbs.heideltime.standalone.components.impl.XMIResultFormatter;
import de.unihd.dbs.heideltime.standalone.exceptions.DocumentCreationTimeMissingException;
import de.unihd.dbs.uima.annotator.heideltime.HeidelTime;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.annotator.heideltime.resources.ResourceMap;
import de.unihd.dbs.uima.annotator.heideltime.resources.ResourceScanner;
import de.unihd.dbs.uima.annotator.intervaltagger.IntervalTagger;
import de.unihd.dbs.uima.types.heideltime.Dct;

/**
 * Servlet implementation class Tagger 
 */
@WebServlet("/TaggerEnglishNarratives")
public class TaggerEnglishNarratives extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private final String treeTaggerHome = TaggerServletConfig.treeTaggerHome;
	private final String chineseTokenizerPath = TaggerServletConfig.chineseTokenizerPath;
	private final String stanfordModelPath = TaggerServletConfig.stanfordModelPath;
	private final String stanfordConfigPath = TaggerServletConfig.stanfordConfigPath;
    private Map<String, ResourceMap> repatterns = new HashMap<String, ResourceMap>();
    private Map<String, ResourceMap> normalizations = new HashMap<String, ResourceMap>();
    private Map<String, ResourceMap> rules = new HashMap<String, ResourceMap>();

    /**
     * Used language
     */
    private Language language = Language.ENGLISH;
    
    /**
     * Used document type
     */
    private DocumentType documentType = DocumentType.valueOf("NARRATIVES");

    /**
     * output format
     */
    private OutputType outputType = OutputType.XMI;
    
    /**
     * HeidelTime instance
     */
    private HeidelTime heidelTime;

    /**
     * Type system description of HeidelTime
     */
    private JCasFactory jcasFactory;

    /**
     * Whether or not to do Interval Tagging
     */
    private Boolean doIntervalTagging = true;

    /**
     * Logging engine
     */
    private static Logger logger = Logger.getLogger("TaggerEnglish*");

	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
	    Properties props = new Properties();

	    props.setProperty("considerDate", "true");
	    props.setProperty("considerDuration", "true");
	    props.setProperty("considerSet", "true");
	    props.setProperty("considerTime", "true");
	    props.setProperty("treeTaggerHome", treeTaggerHome);
	    props.setProperty("chineseTokenizerPath", chineseTokenizerPath);
	    props.setProperty("typeSystemHome", "desc/type/HeidelTime_TypeSystem.xml");
	    props.setProperty("typeSystemHome_DKPro", "desc/type/DKPro_TypeSystem.xml");
	    props.setProperty("uimaVarDate", "Date");
	    props.setProperty("uimaVarDuration", "Duration");
	    props.setProperty("uimaVarLanguage", "Language");
	    props.setProperty("uimaVarSet", "Set");
	    props.setProperty("uimaVarTime", "Time");
	    props.setProperty("uimaVarTypeToProcess", "Type");
	    props.setProperty("sent_model_path", "");
	    props.setProperty("word_model_path", "");
	    props.setProperty("pos_model_path", "");
	    props.setProperty("model_path", stanfordModelPath);
	    props.setProperty("config_path", stanfordConfigPath);
	    props.setProperty("hunpos_path", "");
	    props.setProperty("hunpos_model_name", "");
	    
	    Config.setProps(props);

	    ServletContext context = config.getServletContext();
	    Set<String> resourcesPath = null;

        resourcesPath = context.getResourcePaths("/WEB-INF/resources");

        FilenameFilter txtFilter = new FilenameFilter() {
            @Override
            public boolean accept(File arg0, String arg1) {
                return arg1.endsWith(".txt");
            }
        };
        
        Pattern repatternPattern = Pattern.compile("resources_repattern_(.+)\\.txt$");
        Pattern normalizationPattern = Pattern.compile("resources_normalization_(.+)\\.txt$");
        Pattern rulePattern = Pattern.compile("resources_rules_(.+)\\.txt$");
        for (String s: resourcesPath) {
            //System.out.println(s);
            String lang = new File(s).getName();
            //System.out.println(lang);
            File repatternFolder = new File(context.getRealPath(s), "repattern");
            File normalizationFolder = new File(context.getRealPath(s), "normalization");
            File ruleFolder = new File(context.getRealPath(s), "rules");
            if (!repatternFolder.exists() || !repatternFolder.canRead() || !repatternFolder.isDirectory()
                    || !normalizationFolder.exists() || !normalizationFolder.canRead() || !normalizationFolder.isDirectory() 
                    || !ruleFolder.exists() || !ruleFolder.canRead() || !ruleFolder.isDirectory()) {
                System.err.println("We need at least the folders repattern, normalization and rules in this folder.");
                continue;
            }
            
            File[] repatternFiles = repatternFolder.listFiles(txtFilter);
            File[] normalizationFiles = normalizationFolder.listFiles(txtFilter);
            File[] ruleFiles = ruleFolder.listFiles(txtFilter);
            
            if (repatternFiles.length == 0 || normalizationFiles.length == 0 || ruleFiles.length == 0
                    || !repatternFiles[0].exists() || !repatternFiles[0].canRead() || !repatternFiles[0].isFile()
                    || !normalizationFiles[0].exists() || !normalizationFiles[0].canRead() || !normalizationFiles[0].isFile()
                    || !ruleFiles[0].exists() || !ruleFiles[0].canRead() || !ruleFiles[0].isFile()) {
                System.err.println("We need at least one readable resource file of each type to run.");
                continue;
            }
            
         // at this point, the folder is obviously a language resource folder => collect streams
            repatterns.put(lang, new ResourceMap());
            for(File f : repatternFiles) {
                Matcher m = repatternPattern.matcher(f.getName());
                if(m.matches()) {
                    repatterns.get(lang).putOuterFile(m.group(1), f);
                }
            }
            
            normalizations.put(lang, new ResourceMap());
            for(File f : normalizationFiles) {
                Matcher m = normalizationPattern.matcher(f.getName());
                if(m.matches()) {
                    normalizations.get(lang).putOuterFile(m.group(1), f);
                }
            }
            
            rules.put(lang, new ResourceMap());
            for(File f : ruleFiles) {
                Matcher m = rulePattern.matcher(f.getName());
                if(m.matches()) {
                    rules.get(lang).putOuterFile(m.group(1), f);
                }
            }
        }
        ResourceScanner.getInstance(repatterns, normalizations, rules);
	    
        try {
            heidelTime = new HeidelTime();
            heidelTime.initialize(new UimaContextImpl(language, documentType));
            logger.log(Level.INFO, "HeidelTime initialized");
        } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.WARNING, "HeidelTime could not be initialized");
        }

        // Initialize JCas factory -------------
        logger.log(Level.FINE, "Initializing JCas factory...");
        try {
            TypeSystemDescription[] descriptions = new TypeSystemDescription[] {
                    UIMAFramework
                        .getXMLParser()
                        .parseTypeSystemDescription(
                                new XMLInputSource(context.getResource("/WEB-INF/"+Config.get(Config.TYPESYSTEMHOME))))
                                        //this.getClass()
                                        //.getClassLoader()
                                        //.getResource(Config.TYPESYSTEMHOME)))
            };
            jcasFactory = new JCasFactoryImpl(descriptions);
            logger.log(Level.INFO, "JCas factory initialized");
        } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.WARNING, "JCas factory could not be initialized");
        }
	}
	
	public void heidelTimeInitialize(Language language, DocumentType documentType) {
        try {
            heidelTime = new HeidelTime();
            heidelTime.initialize(new UimaContextImpl(language, documentType));
            logger.log(Level.INFO, "HeidelTime initialized");
        } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.WARNING, "HeidelTime could not be initialized");
        }
	}

    /**
     * Runs the IntervalTagger on the JCAS object.
     * @param jcas jcas object
     */
    private void runIntervalTagger(JCas jcas) {
        logger.log(Level.FINEST, "Running Interval Tagger...");
        Integer beforeAnnotations = jcas.getAnnotationIndex().size();
        
        // Prepare the options for IntervalTagger's execution
        Properties settings = new Properties();
        settings.put(IntervalTagger.PARAM_LANGUAGE, language.getResourceFolder());
        settings.put(IntervalTagger.PARAM_INTERVALS, true);
        settings.put(IntervalTagger.PARAM_INTERVAL_CANDIDATES, false);
        
        // Instantiate and process with IntervalTagger
        IntervalTaggerWrapper iTagger = new IntervalTaggerWrapper();
        iTagger.initialize(settings);
        iTagger.process(jcas);
        
        // debug output
        Integer afterAnnotations = jcas.getAnnotationIndex().size();
        logger.log(Level.FINEST, "Annotation delta: " + (afterAnnotations - beforeAnnotations));
    }

    /**
     * Provides jcas object with document creation time if
     * <code>documentCreationTime</code> is not null.
     * 
     * @param jcas
     * @param documentCreationTime
     * @throws DocumentCreationTimeMissingException
     *             If document creation time is missing when processing a
     *             document of type {@link DocumentType#NEWS}.
     */
    private void provideDocumentCreationTime(JCas jcas,
            Date documentCreationTime)
            throws DocumentCreationTimeMissingException {
        if (documentCreationTime == null) {
            // Document creation time is missing
            if (documentType == DocumentType.NEWS) {
                // But should be provided in case of news-document
                throw new DocumentCreationTimeMissingException();
            }
            if (documentType == DocumentType.COLLOQUIAL) {
                // But should be provided in case of colloquial-document
                throw new DocumentCreationTimeMissingException();
            }
        } else {
            // Document creation time provided
            // Translate it to expected string format
            SimpleDateFormat dateFormatter = new SimpleDateFormat(
                    "yyyy.MM.dd'T'HH:mm");
            String formattedDCT = dateFormatter.format(documentCreationTime);

            // Create dct object for jcas
            Dct dct = new Dct(jcas);
            dct.setValue(formattedDCT);

            dct.addToIndexes();
        }
    }

    /**
     * Establishes preconditions for jcas to be processed by HeidelTime
     * 
     * @param jcas
     */
    private void establishHeidelTimePreconditions(JCas jcas) {
        // Token information & sentence structure
        establishPartOfSpeechInformation(jcas);
    }

    /**
     * Establishes part of speech information for cas object.
     * 
     * @param jcas
     */
    private void establishPartOfSpeechInformation(JCas jcas) {
        logger.log(Level.FINEST, "Establishing part of speech information...");
        System.out.println("POS part");
        Properties settings = new Properties();

        /**
        PartOfSpeechTagger partOfSpeechTagger = new TreeTaggerWrapper();
        settings.put(PartOfSpeechTagger.TREETAGGER_LANGUAGE, language);
        settings.put(PartOfSpeechTagger.TREETAGGER_ANNOTATE_TOKENS, true);
        settings.put(PartOfSpeechTagger.TREETAGGER_ANNOTATE_SENTENCES, true);
        settings.put(PartOfSpeechTagger.TREETAGGER_ANNOTATE_POS, true);
        settings.put(PartOfSpeechTagger.TREETAGGER_IMPROVE_GERMAN_SENTENCES, (language == Language.GERMAN));
        settings.put(PartOfSpeechTagger.TREETAGGER_CHINESE_TOKENIZER_PATH, chineseTokenizerPath);
        **/
        
        PartOfSpeechTagger partOfSpeechTagger = new StanfordPOSTaggerWrapper();
        settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_TOKENS, true);
        settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_SENTENCES, true);
        settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_POS, true);
        settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_MODEL_PATH, Config.get(Config.STANFORDPOSTAGGER_MODEL_PATH));
        settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_CONFIG_PATH, Config.get(Config.STANFORDPOSTAGGER_CONFIG_PATH));

        
        partOfSpeechTagger.initialize(settings);
        partOfSpeechTagger.process(jcas);

/**
        PartOfSpeechTagger partOfSpeechTagger = null;
        Properties settings = new Properties();
        switch (language) {
            case ARABIC:
                partOfSpeechTagger = new StanfordPOSTaggerWrapper();
                settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_TOKENS, true);
                settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_SENTENCES, true);
                settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_POS, true);
                settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_MODEL_PATH, Config.get(Config.STANFORDPOSTAGGER_MODEL_PATH));
                settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_CONFIG_PATH, Config.get(Config.STANFORDPOSTAGGER_CONFIG_PATH));
                break;
            case VIETNAMESE:
                partOfSpeechTagger = new JVnTextProWrapper();
                settings.put(PartOfSpeechTagger.JVNTEXTPRO_ANNOTATE_TOKENS, true);
                settings.put(PartOfSpeechTagger.JVNTEXTPRO_ANNOTATE_SENTENCES, true);
                settings.put(PartOfSpeechTagger.JVNTEXTPRO_ANNOTATE_POS, true);
                settings.put(PartOfSpeechTagger.JVNTEXTPRO_WORD_MODEL_PATH, Config.get(Config.JVNTEXTPRO_WORD_MODEL_PATH));
                settings.put(PartOfSpeechTagger.JVNTEXTPRO_SENT_MODEL_PATH, Config.get(Config.JVNTEXTPRO_SENT_MODEL_PATH));
                settings.put(PartOfSpeechTagger.JVNTEXTPRO_POS_MODEL_PATH, Config.get(Config.JVNTEXTPRO_POS_MODEL_PATH));
                break;
            case CROATIAN:
                partOfSpeechTagger = new HunPosTaggerWrapper();
                settings.put(PartOfSpeechTagger.HUNPOS_LANGUAGE, language);
                settings.put(PartOfSpeechTagger.HUNPOS_ANNOTATE_TOKENS, true);
                settings.put(PartOfSpeechTagger.HUNPOS_ANNOTATE_POS, true);
                settings.put(PartOfSpeechTagger.HUNPOS_ANNOTATE_SENTENCES, true);
                settings.put(PartOfSpeechTagger.HUNPOS_MODEL_PATH, Config.get(Config.HUNPOS_MODEL_PATH));
                break;
            default:
                if(POSTagger.STANFORDPOSTAGGER.equals(posTagger)) {
                    partOfSpeechTagger = new StanfordPOSTaggerWrapper();
                    settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_TOKENS, true);
                    settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_SENTENCES, true);
                    settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_POS, true);
                    settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_MODEL_PATH, Config.get(Config.STANFORDPOSTAGGER_MODEL_PATH));
                    settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_CONFIG_PATH, Config.get(Config.STANFORDPOSTAGGER_CONFIG_PATH));
                } else if(POSTagger.TREETAGGER.equals(posTagger)) {
                    partOfSpeechTagger = new TreeTaggerWrapper();
                    settings.put(PartOfSpeechTagger.TREETAGGER_LANGUAGE, language);
                    settings.put(PartOfSpeechTagger.TREETAGGER_ANNOTATE_TOKENS, true);
                    settings.put(PartOfSpeechTagger.TREETAGGER_ANNOTATE_SENTENCES, true);
                    settings.put(PartOfSpeechTagger.TREETAGGER_ANNOTATE_POS, true);
                    settings.put(PartOfSpeechTagger.TREETAGGER_IMPROVE_GERMAN_SENTENCES, (language == Language.GERMAN));
                    settings.put(PartOfSpeechTagger.TREETAGGER_CHINESE_TOKENIZER_PATH, Config.get(Config.CHINESE_TOKENIZER_PATH));
                } else if(POSTagger.HUNPOS.equals(posTagger)) {
                    partOfSpeechTagger = new HunPosTaggerWrapper();
                    settings.put(PartOfSpeechTagger.HUNPOS_LANGUAGE, language);
                    settings.put(PartOfSpeechTagger.HUNPOS_ANNOTATE_TOKENS, true);
                    settings.put(PartOfSpeechTagger.HUNPOS_ANNOTATE_POS, true);
                    settings.put(PartOfSpeechTagger.HUNPOS_ANNOTATE_SENTENCES, true);
                    settings.put(PartOfSpeechTagger.HUNPOS_MODEL_PATH, Config.get(Config.HUNPOS_MODEL_PATH));
                } else if(POSTagger.NO.equals(posTagger)) {
                    partOfSpeechTagger = new AllLanguagesTokenizerWrapper();
                } else {
                    logger.log(Level.FINEST, "Sorry, but you can't use that tagger.");
                }
        }

        partOfSpeechTagger.initialize(settings);
        partOfSpeechTagger.process(jcas);
**/
        logger.log(Level.FINEST, "Part of speech information established");
    }

    private ResultFormatter getFormatter() {
        if (outputType.toString().equals("xmi")){
            return new XMIResultFormatter();
        } else {
            return new TimeMLResultFormatter();
        }
    }

    /**
     * Processes document with HeidelTime
     *
     * @param document
     * @return Annotated document
     * @throws DocumentCreationTimeMissingException
     *             If document creation time is missing when processing a
     *             document of type {@link DocumentType#NEWS}. Use
     *             {@link #process(String, Date)} instead to provide document
     *             creation time!
     */
    public String process(String document)
            throws DocumentCreationTimeMissingException {
        return process(document, null, getFormatter());
    }

    /**
     * Processes document with HeidelTime
     *
     * @param document
     * @return Annotated document
     * @throws DocumentCreationTimeMissingException
     *             If document creation time is missing when processing a
     *             document of type {@link DocumentType#NEWS}. Use
     *             {@link #process(String, Date)} instead to provide document
     *             creation time!
     */
    public String process(String document, Date documentCreationTime)
            throws DocumentCreationTimeMissingException {
        return process(document, documentCreationTime, getFormatter());
    }

    /**
     * Processes document with HeidelTime
     * 
     * @param document
     * @return Annotated document
     * @throws DocumentCreationTimeMissingException
     *             If document creation time is missing when processing a
     *             document of type {@link DocumentType#NEWS}. Use
     *             {@link #process(String, Date)} instead to provide document
     *             creation time!
     */
    public String process(String document, ResultFormatter resultFormatter)
            throws DocumentCreationTimeMissingException {
        return process(document, null, resultFormatter);
    }

    /**
     * Processes document with HeidelTime
     * 
     * @param document
     * @param documentCreationTime
     *            Date when document was created - especially important if
     *            document is of type {@link DocumentType#NEWS}
     * @return Annotated document
     * @throws DocumentCreationTimeMissingException
     *             If document creation time is missing when processing a
     *             document of type {@link DocumentType#NEWS}
     */
    public String process(String document, Date documentCreationTime, ResultFormatter resultFormatter)
            throws DocumentCreationTimeMissingException {
        logger.log(Level.INFO, "Processing started");

        // Generate jcas object ----------
        logger.log(Level.FINE, "Generate CAS object");
        JCas jcas = null;
        try {
            jcas = jcasFactory.createJCas();
            jcas.setDocumentText(document);
            logger.log(Level.FINE, "CAS object generated");
        } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.WARNING, "Cas object could not be generated");
        }

        // Process jcas object -----------
        try {
            logger.log(Level.FINER, "Establishing preconditions...");
            provideDocumentCreationTime(jcas, documentCreationTime);
            establishHeidelTimePreconditions(jcas);
            logger.log(Level.FINER, "Preconditions established");

            heidelTime.process(jcas);

            logger.log(Level.INFO, "Processing finished");
        } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.WARNING, "Processing aborted due to errors");
        }

        // process interval tagging ---
        if(doIntervalTagging)
            runIntervalTagger(jcas);
        
        // Process results ---------------
        logger.log(Level.FINE, "Formatting result...");
        // PrintAnnotations.printAnnotations(jcas.getCas(), System.out);
        String result = null;
        try {
            result = resultFormatter.format(jcas);
            logger.log(Level.INFO, "Result formatted");
        } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.WARNING, "Result could not be formatted");
        }

        return result;
    }
	
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    request.setCharacterEncoding("UTF-8");
	    
	    Date dct = null;
	    String dateParam = request.getParameter("date");
	    if (dateParam != null) {
	        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
	        try {
	            dct = formatter.parse(dateParam);
	        } catch (ParseException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	        }
	    } else {
	        if ((documentType == DocumentType.NEWS) || (documentType == DocumentType.COLLOQUIAL)) {
	            // Dct needed
	            dct = new Date();
	        }
	    }

	    String input = "";
	    if (request.getParameter("q") != null) {
	        input = request.getParameter("q");
	    }
	    if (input.equals("")) {
	        return;
	    }
	    
	    String out = null;
	    try {
	        out = process(input, dct);
	    } catch (DocumentCreationTimeMissingException e) {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
	    }
	    
	    JSONObject xmlJSONObj = XML.toJSONObject(out);
	    //System.out.println(xmlJSONObj.toString());
	    
	    //System.out.println(out);
	        
	    // Print output as JSON
	    response.setContentType("application/json");
	    response.getOutputStream().println(xmlJSONObj.toString()); 
	}

}
