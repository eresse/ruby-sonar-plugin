package com.godaddy.sonar.ruby.metricfu;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.config.Settings;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.PersistenceMode;
import org.sonar.api.measures.RangeDistributionBuilder;
import org.sonar.api.resources.Project;
import org.sonar.api.scan.filesystem.PathResolver;

import com.godaddy.sonar.ruby.RubyPlugin;
import com.google.common.collect.Lists;

public class MetricfuComplexitySensor implements Sensor
{
  private static final Logger          LOG                             = LoggerFactory
                                                                           .getLogger(MetricfuComplexitySensor.class);

  private MetricfuComplexityYamlParser metricfuComplexityYamlParser;
  private Settings                     settings;
  private FileSystem                   fs;

  private static final Number[]        FILES_DISTRIB_BOTTOM_LIMITS     = { 0, 5, 10, 20, 30, 60, 90 };
  private static final Number[]        FUNCTIONS_DISTRIB_BOTTOM_LIMITS = { 1, 2, 4, 6, 8, 10, 12, 20, 30 };

  private String                       reportPath                      = "tmp/metric_fu/report.yml";
  private PathResolver                 pathResolver;

  public MetricfuComplexitySensor(Settings settings, FileSystem fs,
      PathResolver pathResolver,
      MetricfuComplexityYamlParser metricfuComplexityYamlParser) {
    this.settings = settings;
    this.fs = fs;
    this.metricfuComplexityYamlParser = metricfuComplexityYamlParser;
    this.pathResolver = pathResolver;
    String reportpath_prop = settings.getString(RubyPlugin.METRICFU_REPORT_PATH_PROPERTY);
    if (null != reportpath_prop) {
      this.reportPath = reportpath_prop;
    }
  }

  public boolean shouldExecuteOnProject(Project project)
  {
    return fs.hasFiles(fs.predicates().hasLanguage("ruby"));
  }

  public void analyse(Project project, SensorContext context)
  {
    File report = pathResolver.relativeFile(fs.baseDir(), reportPath);
    LOG.info("Calling analyse for report results: " + report.getPath());
    if (!report.isFile()) {
      LOG.warn("MetricFu report not found at {}", report);
      return;
    }

    List<InputFile> sourceFiles = Lists.newArrayList(fs.inputFiles(fs.predicates().hasLanguage("ruby")));

    for (InputFile inputFile : sourceFiles)
    {
      LOG.debug("analyzing functions for classes in the file: " + inputFile.file().getName());
      try
      {
        analyzeFile(inputFile, context, report);
      } catch (IOException e)
      {
        LOG.error("Can not analyze the file " + inputFile.absolutePath() + " for complexity", e);
      }
    }
  }

  private void analyzeFile(InputFile inputFile, SensorContext sensorContext, File resultsFile)
      throws IOException
  {
    LOG.info("functions are set");
    String complexityType = settings.getString(RubyPlugin.METRICFU_COMPLEXITY_METRIC_PROPERTY);
    List<RubyFunction> functions = metricfuComplexityYamlParser.parseFunctions(inputFile.file().getName(), resultsFile, "Saikuro");
    // if function list is empty, then return, do not compute any complexity
    // on that file
    if (functions.isEmpty() || functions.size() == 0 || functions == null)
    {
      return;
    }

    // COMPLEXITY
    LOG.info("COMPLEXITY are set" + functions.toString());
    int fileComplexity = 0;
    for (RubyFunction function : functions)
    {
      fileComplexity += function.getComplexity();
      LOG.info("File complexity " + fileComplexity);
    }
    sensorContext.saveMeasure(inputFile, CoreMetrics.COMPLEXITY, Double.valueOf(fileComplexity));
    sensorContext.saveMeasure(inputFile, CoreMetrics.COMPLEXITY_IN_FUNCTIONS, Double.valueOf(fileComplexity) / functions.size());

    RangeDistributionBuilder fileDistribution = new RangeDistributionBuilder(CoreMetrics.FILE_COMPLEXITY_DISTRIBUTION, FILES_DISTRIB_BOTTOM_LIMITS);
    fileDistribution.add(Double.valueOf(fileComplexity));
    sensorContext.saveMeasure(inputFile, fileDistribution.build().setPersistenceMode(PersistenceMode.MEMORY));

    RangeDistributionBuilder functionDistribution = new RangeDistributionBuilder(CoreMetrics.FUNCTION_COMPLEXITY_DISTRIBUTION, FUNCTIONS_DISTRIB_BOTTOM_LIMITS);
    for (RubyFunction function : functions)
    {
      functionDistribution.add(Double.valueOf(function.getComplexity()));
    }
    sensorContext.saveMeasure(inputFile, functionDistribution.build().setPersistenceMode(PersistenceMode.MEMORY));
  }
}
