package ml.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class DataFormatter {
	
	private final int ALT = 0;
	private final int COMP = 1;
	private final int MISC = 2;
	private final int FOR_SALE = 3;
	private final int AUTOS = 4; //cars and motorcycles
	private final int SPORTS = 5; //hockey and baseball
	private final int SCIENCE = 6; //cryptology, medicine, electronics, space
	private final int SOC_RELIGION = 7;
	private final int POLITICS = 8;
	private final int TALK_RELIGION = 9;
	
	
	private ArrayList<File> filesToAggregate = new ArrayList<File>();
	
	public static void main (String args[]) throws IOException {
//		if ( args.length!= 1 ) {
//			System.err.println("FormatData <inputDir> ");
//		}
		
//		File test = new File("/Users/philipchang/Documents/csci/cs451/project/bin/test.txt");
//		parseText(test);
		String path = "/Users/philipchang/Documents/csci/cs451/cs451_project/20news-bydate/20news-bydate-test";
			
		DataFormatter formatter = new DataFormatter();
		formatter.init(path);
			
		formatter.writeOutput("output.txt");	
	}
	
	/**
	 * Takes in a directory and initializes an ArrayList with the pathnames of all the files
	 * @param path
	 */
	public void init(String path) {
		File dir = new File(path);
		
		System.out.println("is Directory? " + dir.isDirectory());
		System.out.println("is File? " + dir.isFile());
		System.out.println("name: " + dir.getName());
		
		if (dir.exists()) {
			if (dir.isDirectory()) {
				getFiles(dir);
			}
		}
		
		for (File f : filesToAggregate) {
			System.out.println(f.getName());
		}
	}
	
	/** 
	 * Function that recursively gets all sub files of a root folder until it finds a file (not a directory)
	 * @param root
	 */
	private void getFiles(File root) {
		if ( root.isDirectory() ) {
			for ( File child : root.listFiles() ) {
				getFiles(child);
			}
		}
		else { // isFile
			if (!root.isHidden()) { //so it does not include .DS_Store
				filesToAggregate.add(root);
			}
		}
	}
	
	/**
	 * Writes output to specified location
	 * Text is separated by line, and is parsed based on whether it is useful content
	 * @param output_file
	 * @throws IOException
	 */
	public void writeOutput(String output_file) throws IOException{
		FileWriter output = new FileWriter(output_file);
		for ( File f : filesToAggregate ) {
			//assign correct label based on file pathname
			if (f.getAbsolutePath().contains(".atheism"))
				output.append(Integer.toString(ALT));
			else if (f.getAbsolutePath().contains("comp.")) 
				output.append(Integer.toString(COMP));
			else if (f.getAbsolutePath().contains(".forsale"))
				output.append(Integer.toString(FOR_SALE));
			else if (f.getAbsolutePath().contains(".autos") || f.getAbsolutePath().contains(".motorcycles"))
				output.append(Integer.toString(AUTOS));
			else if (f.getAbsolutePath().contains("sport."))
				output.append(Integer.toString(SPORTS));
			else if (f.getAbsolutePath().contains("sci."))
				output.append(Integer.toString(SCIENCE));
			else if (f.getAbsolutePath().contains("soc.religion"))
				output.append(Integer.toString(SOC_RELIGION));
			else if (f.getAbsolutePath().contains("politics."))
				output.append(Integer.toString(POLITICS));
			else
				output.append(Integer.toString(TALK_RELIGION));
			
			
			output.append("\t");
			
			output.append(parseText(f));
			output.append('\n');
		}
		
		output.close();
	}

	/**
	 * Takes in a file and parses the file's text into only the useful information
	 * Specifically, looks for the line that says how many lines (n) there are in the post
	 * and takes the last n lines of the file
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static String parseText(File file) throws IOException {
		StringBuilder builder = new StringBuilder();
		BufferedReader reader = new BufferedReader(new FileReader(file));
		
		ArrayList<String> lines = new ArrayList<String>();
		String nextLine = reader.readLine();
		int textLines = 0;
		
		while (nextLine != null) {
			lines.add(nextLine);
			if (nextLine.contains("Lines:")) {
				String[] parts = nextLine.split(": ");

				try {
					textLines = Integer.parseInt(parts[1]);
				}
				catch (NumberFormatException n) {
					System.err.println("Not a number following lines: ");
					textLines = 0;
				}
			}
			
			if (nextLine.contains("Subject:")) { // we want the subject headers
				String[] parts = nextLine.split(": ");
				int last = parts.length - 1;
				String subject_line = parseWords(parts[last]);
				builder.append(subject_line);
			}
			
			nextLine = reader.readLine();
		}
		reader.close();
		
		if (textLines == 0) { //if the article does not provide the number of lines, we just use the whole thing
			textLines = lines.size();
		}
		
		for ( int i = 1; i <= textLines; i++) {
			try {
				String toAdd = lines.get(lines.size()-i);
				if (!toAdd.matches("[\\s]*")) { //check if it is just an empty line
					String words = parseWords(toAdd);
					builder.append(words);
				}
			}
			catch (ArrayIndexOutOfBoundsException a) {
				System.err.println("For some reason there weren't enough lines");
			}
		}
		
		String text = builder.toString();
		System.out.println(file.getName());
		return text;
	}
	
	private static String parseWords(String line) {
		StringBuilder builder = new StringBuilder();
		String words[] = line.split(" ");
		for ( String w : words ) {
			if ( !w.matches("[\\W]*") ) { //do not add if word is just non-word symbols
				String word_parts[] = w.split("\\W");
				for (String p : word_parts) { //get rid of non-word characters
					builder.append(p);
					builder.append(" ");
				}
			}
		}
		
		return builder.toString();
	}
	
}
