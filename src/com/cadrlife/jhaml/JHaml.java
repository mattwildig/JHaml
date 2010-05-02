package com.cadrlife.jhaml;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.cadrlife.jhaml.util.IndentUtils;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;


public class JHaml {
	private Helper helper;
	private int indentationSize = -1;
	private boolean isIndentWithTabs = false;
	private final JHamlConfig config;
	
	public JHaml() {
		this(new JHamlConfig());
	}
	public JHaml(JHamlConfig config) {
		this.config = config;
		helper = new Helper(config);
	}
	
	public String parse(String input) {
		helper.errorChecker.validateConfig(this.config);
		indentationSize = -1;
		isIndentWithTabs = false;
		if (StringUtils.isBlank(input.trim())) {
			return "";
		}
		input = preProcess(input);
		try {
			List<Line> lines = new JHamlParser(new StringReader(input)).jHamlSource();
			helper.errorChecker.checkDocumentDoesNotBeginWithIndentation(lines);
			List<Line> lineTree = processNesting(lines);
			return postProcess(renderLines(lineTree));
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
	
	private String preProcess(String input) {
		return normalizeWhitespace(normalizeLineBreaks(input));
	}

	private String normalizeWhitespace(String input) {
		char nonBreakingSpace = (char)160;
		char space = ' ';
		return input.replaceAll("" + nonBreakingSpace, "" + space);
	}
	private String normalizeLineBreaks(String input) {
		return input.replaceAll("\r\n", "\n").replaceAll("\n\r", "\n").replaceAll("\r", "\n") + "\n";
	}
	private String postProcess(String string) {
		return string.replaceAll("\n\n+", "\n").trim().replaceAll("<%\\s*\\}\\s*%>\\s*<%\\s*else", "<% } else");
	}
	
	private String renderLines(List<Line> lineTree) {
		String result = "";
		for (Line line : lineTree) {
			result += renderLine(line) + "\n";
		}
		return result;
	}

	private List<Line> processNesting(List<Line> lines) {
		int indentSize = -1;
		List<Line> lineTree = new ArrayList<Line>();
		for (Line line : lines) {
			
			Line parentLine = parentLine(line, lineTree);
			if (parentLine != null) {
				if (indentSize == -1) {
					indentSize = line.leadingWhitespace.length() - parentLine.leadingWhitespace.length();
				}
				line.indentation = parentLine.indentation + "  ";
				if (parentLine.isFilter()) {
					line.isWithinFilter = true;
				}
				parentLine.block.add(line);
			} else {
				lineTree.add(line);
			}
		}
		return lineTree;
	}
	
	private Line parentLine(Line childLine, List<Line> lineTree) {
		Line lastLine = null;
		for (Line line : lineTree) {
			if (!line.isBlank() && !line.isWithinFilter) {
				lastLine = line;
			}
		}
		if (lastLine != null) {
			if (isDeeper(lastLine, childLine) || childLine.isBlank()) {
				if (childLine.isBlank() && !lastLine.canHaveNesting()) {
					return null;
				}
				Line innerParent = parentLine(childLine, lastLine.block);
				return innerParent == null ? lastLine : innerParent;
			}
		}
		return null;
	}

	private boolean isDeeper(Line previousLine, Line line) {
		return previousLine.leadingWhitespace.length() < line.leadingWhitespace.length();
	}
	
	private String renderLine(Line line) {
		for (Line nestedLine : line.block) {
			validateIndentation(line,nestedLine);
		}
		String nestedContent = renderLines(line.block);
		String content = line.inlineContent + (StringUtils.isBlank(nestedContent) ? "" : "\n" + IndentUtils.indent(nestedContent,JHamlConfig.OUTPUT_INDENTATION_SIZE));
		if (line.isElement()) {
			helper.errorChecker.setCurrentLineNumber(line.lineNumber);
			helper.mergeAttributes(line);
			String parsedContent = helper.parseFreeFormText(line, "", content);
			parsedContent = parsedContent.replaceAll("\\n+\\s*\\z", "\n");
			String element = helper.elem(line, parsedContent, line.isSelfClosing);
			return element;
		}
		if (line.isStatement() || line.isComment()) {
			if (line.hasNestedContent()) {
				return helper.parseFreeFormText(line, "",line.inlineContent + "\n" + IndentUtils.indent(renderLines(line.block),JHamlConfig.OUTPUT_INDENTATION_SIZE));
			}
			return helper.parseFreeFormText(line, "",line.inlineContent);
		}
		
		String textBlock = textBlock(line);
		return helper.parseFreeFormText(line, "", textBlock);
	}

	private String textBlock(Line line) {
		String result = line.text.substring(line.indentation.length());
		List<String> content = new ArrayList<String>();
		for (Line contentLine : line.block) {
			content.add(textBlock(contentLine));
		}
		return result + (content.isEmpty() ? "" : "\n") + Joiner.on("\n").join(content);
	}
	
	public void validateIndentation(Line parentLine, Line line) {
		int parentIndent = parentLine.indentation.length();
		this.helper.errorChecker.setCurrentLineNumber(line.lineNumber);
		line.indentation = line.leadingWhitespace;
		if (StringUtils.isEmpty(line.indentation)|| line.isBlank()) {
			return;
		}
		if (indentationSize == -1 && StringUtils.isNotEmpty(line.indentation)) {
			indentationSize = line.indentation.length();
			isIndentWithTabs =  CharMatcher.is('\t').matchesAllOf(line.indentation);
			this.helper.errorChecker.checkInitialIndentation(line.indentation);
		}
		int nextLevel = parentIndent + indentationSize;
		if (parentLine.isFilter() && line.indentation.length() > nextLevel) {
			line.indentation = line.indentation.substring(0, nextLevel);
			line.inlineContent = line.indentation.substring(nextLevel) + line.inlineContent;
		}
		this.helper.errorChecker.checkIndentationIsConsistent(indentationSize,isIndentWithTabs,parentIndent, line.leadingWhitespace, line.indentation);
	}
}