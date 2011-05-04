package com.cadrlife.jhaml.ant;

import java.io.*;
import java.util.Iterator;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FilenameUtils;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.Resource;

import com.cadrlife.jhaml.JHaml;

public class JHamlAntTask extends MatchingTask {
	
	private static final String HAML_EXTENSION = "haml";
	private static final String HTML_EXTENSION = "html";
	
	private File outputDir;
	
	public void setBasedir(File basedir) {
		fileset.setDir(basedir);
	}
	
	public void setDestdir(File destdir) {
		outputDir = destdir;
	}
	
	public void execute() throws BuildException{
		
		validate();
		
		for(Iterator it = fileset.iterator(); it.hasNext(); ) {
			Resource r = (Resource)it.next();
			
			String haml = null;
			try {
				//default charset for now
				BufferedReader reader = new BufferedReader(new InputStreamReader(r.getInputStream()));
				StringWriter writer = new StringWriter();
				
				IOUtils.copy(reader, writer);
				String html = new JHaml().parse(writer.toString());
				
				String hamlFilename = r.getName();
				String ext = FilenameUtils.getExtension(hamlFilename);
				String htmlFilename = null;
				
				if (HAML_EXTENSION.equals(ext)){
					String strippedName = FilenameUtils.removeExtension(hamlFilename);
					
					if (HTML_EXTENSION.equals(FilenameUtils.getExtension(strippedName))) {
						//filename.html.haml -> filename.html
						htmlFilename = strippedName;
					}
					else {
						//filename.haml -> filename.html
						htmlFilename = strippedName + "." + HTML_EXTENSION;
					}
				}
				else {
					//just append "html" for unknown extensions
					//filename.xyz -> filename.xyz.html
					htmlFilename = hamlFilename + "." + HTML_EXTENSION;
				}
				
				File outfile = new File(outputDir, htmlFilename);
				
				//again: default charset for now
				IOUtils.write(html, new FileOutputStream(outfile));
				
				
			}
			catch (IOException ioe) {
				throw new BuildException(ioe);
			}
			
			
		}
	}
	
	private void validate() throws BuildException {
		if (! (outputDir != null && outputDir.exists() && outputDir.isDirectory())) {
			throw new BuildException("destdir must be set and must be a directory");
		}
	}
}
