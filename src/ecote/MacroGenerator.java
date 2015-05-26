package ecote;
import ecote.Exceptions.*;

import java.util.*;
import java.io.*;


public class MacroGenerator {

    private String inputFile;
    private String outputFile;
    private String logFile;

    private static final char HASH = '#';
    private static final char OPENING_BRACKET = '(';
    private static final char CLOSING_BRACKET = ')';
    private static final char OPENING_CURVE_BRACKET = '{';
    private static final char CLOSING_CURVE_BRACKET = '}';
    private static final char DOLLAR = '$';
    private static final char AMPERSANT = '&';
    private static final char COMMA = ',';
    private static final char EOF = '\n';
    private static final String MISSING_PARAMS_STRING = "MISSING_PARAMETER";

    private String macroDefOrCallToCheck = "";
    private int currentLine = 1;
    private int startLine;
    private static final MacroLib macLib = new MacroLib();

    private int readChar;
    private int prevReadChar;

    FileReader inputStream = null;
    FileWriter outputStream = null;
    FileWriter logStream = null;


    public MacroGenerator(String inputFile, String outputFile, String logFile){
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.logFile = logFile;
    }

    public void read() throws IOException {
        try {

            inputStream = new FileReader(inputFile);
            outputStream = new FileWriter(outputFile);
            logStream = new FileWriter(logFile);

            try {
                while(getChar() != -1) {
                    if((char)readChar == HASH && prevReadChar == EOF){
                        try {
                            startLine = currentLine;
                            readMacros();
                        } catch (IllegalMacroDefinition e) {
                            errorLogging(e.getMessage(macroDefOrCallToCheck, startLine));
                        }
                    }
                    else if((char)readChar == DOLLAR && prevReadChar == EOF) {
                        try {
                            startLine = currentLine;
                            callMacros();
                        } catch (IllegalMacroCall e) {
                            errorLogging(e.getMessage(macroDefOrCallToCheck, startLine));
                        }
                    }
                    else{
                        outputStream.write(readChar);
                    }
                } //end of while
                checkIfAllMacrosesUsed();
            } finally {
                inputStream.close();
                outputStream.close();
                logStream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    private void createStringToCheckDefinition(){
        macroDefOrCallToCheck += (char)readChar;
    }

    private void readMacros() throws IOException, IllegalMacroDefinition {

        macroDefOrCallToCheck = Character.toString((char)readChar);

        List<Integer> defParamValue = new ArrayList<Integer>();
        List<Integer> bodyParamValue = new ArrayList<Integer>();
        List<String> freeText = new ArrayList<String>();

        String freeTextParam = "";
        String name = "";

        while ((char)getChar() != OPENING_BRACKET){
            name += (char)readChar;
            createStringToCheckDefinition();
        }
        createStringToCheckDefinition();




        while ((char)getChar() != CLOSING_BRACKET){
            createStringToCheckDefinition();
            if((char)readChar == AMPERSANT) {
                defParamValue.add(Character.getNumericValue(getChar()));
                createStringToCheckDefinition();
            }
        }
        createStringToCheckDefinition();



        while((char)getChar() != OPENING_CURVE_BRACKET) {
            createStringToCheckDefinition();
        }
        createStringToCheckDefinition();



        while((char)getChar() != CLOSING_CURVE_BRACKET){
            createStringToCheckDefinition();
            if((char)readChar == AMPERSANT){
                freeText.add(freeTextParam);
                freeTextParam = "";
                bodyParamValue.add(Character.getNumericValue(getChar()));
                createStringToCheckDefinition();
            }
            else {
                freeTextParam += (char)readChar;
            }
        }
        createStringToCheckDefinition();

        //all checking starts here ->
        String regexForMacroDefinition = "#\\w+\\((\\s*(&[1-9])\\s*,?)+\\s*\\)\\s*\\{(\\s*\\w+\\s*&[1-9])+\\s*\\}";
        if(!macroDefOrCallToCheck.matches(regexForMacroDefinition) || defParamValue.size() != bodyParamValue.size()){
            throw new IllegalMacroDefinition();
        }
        else {
            for(int i = 0; i < defParamValue.size(); i++){
                if(!defParamValue.get(i).equals(bodyParamValue.get(i))) {
                    throw new IllegalMacroDefinition("Parameter list is not equal in body and definition!");
                }
            }
        }

        addMacrosToLib(name, defParamValue.size(), freeText.toArray(new String[freeText.size()]));

    }


    private void callMacros() throws IOException, IllegalMacroCall {
        macroDefOrCallToCheck = Character.toString((char)readChar);
        String macroName = "";
        List<String> paramsList = new ArrayList<String>();
        String parameter = "";

        while((char)getChar() != OPENING_BRACKET) {
            macroName += (char)readChar;
            createStringToCheckDefinition();
        }
        createStringToCheckDefinition();



        while((char)getChar() != CLOSING_BRACKET) {
            createStringToCheckDefinition();
            if((char)readChar == COMMA){
                paramsList.add(parameter);
                parameter = "";
            }
            else{
                parameter += (char)readChar;
            }
        }
        createStringToCheckDefinition();
        paramsList.add(parameter);


        String regexForMacroCall = "\\$\\w+\\(\\s*(\\w*\\s*,?)+\\s*\\)";

        if(!macroDefOrCallToCheck.matches(regexForMacroCall)){
            throw new IllegalMacroCall();
        }

        try {
            Macro m = macLib.getMacros(macroName);
            String[] freeText = m.getFreeText();

            int mcNumberParameters = m.getNumberOfParameters();
            int callNumberParameters = paramsList.size();

            try {
                countDifference(callNumberParameters, mcNumberParameters);
                for(int k = 0, l = 0; k < callNumberParameters; k++, l++){
                    if(l == mcNumberParameters){
                        l = 0;
                    }
                    outputStream.write(freeText[l].toCharArray());
                    outputStream.write(paramsList.get(k).toCharArray());
                }
            } catch (NotEnoughParameters e) {

                errorLogging(e.getMessage(m.getName(), m.getNumberOfParameters(), paramsList.size(), MISSING_PARAMS_STRING, startLine));

                for(int k = 0; k < mcNumberParameters; k++){
                    outputStream.write(freeText[k].toCharArray());
                    if(k >= callNumberParameters){
                        outputStream.write((MISSING_PARAMS_STRING + "_#" + k).toCharArray());
                    }
                    else {
                        outputStream.write(paramsList.get(k).toCharArray());
                    }
                }
            }
        } catch (MacrosNotFound e) {
            errorLogging(e.getMessage(macroName, startLine));
        }

    }

    private int countDifference(int paramsFromCall, int paramsFromDeffinition) throws NotEnoughParameters {
        int difference = paramsFromCall - paramsFromDeffinition;
        if(difference < 0){
            throw new NotEnoughParameters();
        }
        else {
            return difference;
        }
    }

    private void addMacrosToLib(String macroName, int numberIfParamiters, String[] freeText) throws IOException {
        try {
            macLib.addMacro(macroName, numberIfParamiters, freeText);
        } catch (MacrosNameIsAlreadyUsed e){
            errorLogging(e.getMessage(macroName, startLine));
        }
    }

    private void checkIfAllMacrosesUsed() throws IOException {
        List<Macro> unusedMacro = new ArrayList<Macro>(macLib.unusedMacroses());
        for(Macro m: unusedMacro){
            errorLogging("Warning!: Macros <" + m.getName() + "> has been declared but never used!");
        }
    }

    private int getChar() throws IOException {
        prevReadChar = readChar;

        if((char)prevReadChar == '\n'){
            currentLine++;
        }
        return (readChar = inputStream.read());
    }

    private void errorLogging(String errorMessage) throws IOException {
        System.err.println(errorMessage);
        logStream.write(errorMessage + "\n");
    }

}
