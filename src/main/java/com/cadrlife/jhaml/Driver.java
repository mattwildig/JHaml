package com.cadrlife.jhaml;

import java.io.*;

public class Driver {
	
	public static void main(String[] args) throws IOException{
		
		String fileName = args[0];
		//assume default charset for now
		Reader reader = new BufferedReader(new FileReader(fileName));
		StringBuilder builder = new StringBuilder();
		char[] buf = new char[2048];
		int read;
		
		while ((read = reader.read(buf, 0, buf.length)) > 0) {
			builder.append(buf, 0, read);
		}
		
		String out = new JHaml().parse(builder.toString());
		
		System.out.print(out);
		System.out.println();
	}
}