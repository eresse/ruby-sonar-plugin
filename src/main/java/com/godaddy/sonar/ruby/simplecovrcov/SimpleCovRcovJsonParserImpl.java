package com.godaddy.sonar.ruby.simplecovrcov;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.measures.CoverageMeasuresBuilder;

import com.godaddy.sonar.ruby.simplecovrcov.SimpleCovRcovJsonParser;
import com.google.common.collect.Maps;

public class SimpleCovRcovJsonParserImpl implements SimpleCovRcovJsonParser
{
    private static final Logger LOG = LoggerFactory.getLogger(SimpleCovRcovJsonParserImpl.class);

    public Map<String, CoverageMeasuresBuilder> parse(File file) throws IOException
    {
        Map<String, CoverageMeasuresBuilder> coveredFiles = Maps.newHashMap();

        File fileToFindCoverage = file;

        String fileString = FileUtils.readFileToString(fileToFindCoverage, "UTF-8");

	JsonParser p = new JsonParser();
	JsonObject resultJsonObject = p.parse(fileString).getAsJsonObject();

	// Determine Root Node
	String coverageRootNode = "";
	if(resultJsonObject.get("RSpec") != null) { coverageRootNode = "RSpec"; }
	if(resultJsonObject.get("MiniTest") != null) { coverageRootNode = "MiniTest"; }

	// Check Coverage Root Node
	if(!(coverageRootNode.equals("")))
	{
		JsonObject coverageJsonObj = resultJsonObject.get(coverageRootNode).getAsJsonObject().get("coverage").getAsJsonObject();

	        // for each file in the coverage report
	        for (int j = 0; j < coverageJsonObj.entrySet().size(); j++)
	        {
	            CoverageMeasuresBuilder fileCoverage = CoverageMeasuresBuilder.create();

	            String filePath = ((Map.Entry)coverageJsonObj.entrySet().toArray()[j]).getKey().toString();

	            JsonArray coverageArray = coverageJsonObj.get(filePath).getAsJsonArray();

	            // for each line in the coverage array
	            for (int i = 0; i < coverageArray.size(); i++)
	            {
			Long line = null;
			if(!coverageArray.get(i).isJsonNull()) { line = coverageArray.get(i).getAsLong(); }

	                Integer intLine = 0;
	                int lineNumber = i + 1;
	                if (line != null)
	                {
	                    intLine = line.intValue();
	                    fileCoverage.setHits(lineNumber, intLine);
	                }
	            }
	            LOG.info("FILE COVERAGE = " + fileCoverage.getCoveredLines());
	            coveredFiles.put(filePath, fileCoverage);
	        }
	}
        return coveredFiles;
    }
}
