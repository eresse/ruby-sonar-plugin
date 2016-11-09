package com.godaddy.sonar.ruby;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.config.Settings;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.resources.Project;
import org.sonar.squid.measures.Metric;
import org.sonar.squid.text.Source;

import com.godaddy.sonar.ruby.core.RubyFile;
import com.godaddy.sonar.ruby.core.RubyPackage;
import com.godaddy.sonar.ruby.core.RubyRecognizer;
import com.godaddy.sonar.ruby.parsers.CommentCountParser;
import com.google.common.collect.Lists;

public class RubySensor implements Sensor
{
  // private ModuleFileSystem moduleFileSystem;
  private Settings   settings;
  private FileSystem fs;

  public RubySensor(Settings settings, FileSystem fs) {
    this.settings = settings;
    this.fs = fs;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    // This sensor is executed only when there are Ruby files
    return fs.hasFiles(fs.predicates().hasLanguage("ruby"));
  }

  public void analyse(Project project, SensorContext context)
  {
    computeBaseMetrics(context, project);
  }

  protected void computeBaseMetrics(SensorContext sensorContext, Project project)
  {
    Reader reader = null;
    FilePredicate filePredicate = fs.predicates().hasLanguage("ruby");
    List<InputFile> sourceFiles = Lists.newArrayList(fs.inputFiles(filePredicate));

    Set<RubyPackage> packageList = new HashSet<RubyPackage>();
    for (InputFile rubyFile : sourceFiles)
    {
      try
      {
        File fileRuby = rubyFile.file();
        reader = new StringReader(FileUtils.readFileToString(fileRuby, fs.encoding().name()));
        RubyFile resource = new RubyFile(fileRuby, sourceFiles);
        Source source = new Source(reader, new RubyRecognizer());
        packageList.add(new RubyPackage(resource.getParent().getKey()));

        sensorContext.saveMeasure(rubyFile, CoreMetrics.NCLOC, (double) source.getMeasure(Metric.LINES_OF_CODE));
        int numCommentLines = CommentCountParser.countLinesOfComment(fileRuby);

        sensorContext.saveMeasure(rubyFile, CoreMetrics.COMMENT_LINES, (double) numCommentLines);
        sensorContext.saveMeasure(rubyFile, CoreMetrics.FILES, 1.0);
        sensorContext.saveMeasure(rubyFile, CoreMetrics.CLASSES, 1.0);
      } catch (Exception e)
      {
        throw new IllegalStateException("Error computing base metrics for project.", e);
      } finally
      {
        IOUtils.closeQuietly(reader);
      }
    }
    for (RubyPackage pack : packageList)
    {
      sensorContext.saveMeasure(pack, CoreMetrics.DIRECTORIES, 1.0);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName();
  }
}
