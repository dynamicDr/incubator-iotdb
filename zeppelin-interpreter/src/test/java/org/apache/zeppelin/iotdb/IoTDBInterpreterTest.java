/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.zeppelin.iotdb;


import static org.apache.zeppelin.iotdb.IoTDBInterpreter.DEFAULT_HOST;
import static org.apache.zeppelin.iotdb.IoTDBInterpreter.DEFAULT_PORT;
import static org.apache.zeppelin.iotdb.IoTDBInterpreter.IOTDB_HOST;
import static org.apache.zeppelin.iotdb.IoTDBInterpreter.IOTDB_PASSWORD;
import static org.apache.zeppelin.iotdb.IoTDBInterpreter.IOTDB_PORT;
import static org.apache.zeppelin.iotdb.IoTDBInterpreter.IOTDB_USERNAME;

import java.io.IOException;
import java.util.Properties;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class IoTDBInterpreterTest {

  private IoTDBInterpreter interpreter;

  @Before
  public void open() {
    EnvironmentUtils.closeStatMonitor();
    EnvironmentUtils.envSetUp();
    Properties properties = new Properties();
    properties.put(IOTDB_HOST, DEFAULT_HOST);
    properties.put(IOTDB_PORT, DEFAULT_PORT);
    properties.put(IOTDB_USERNAME, "root");
    properties.put(IOTDB_PASSWORD, "root");
    interpreter = new IoTDBInterpreter(properties);
    interpreter.open();
    initInsert();
  }

  private void initInsert() {
    interpreter.interpret(
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (1, 1.1, false, 11)",
        null);
    interpreter.interpret(
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (2, 2.2, true, 22)",
        null);
    interpreter.interpret(
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (3, 3.3, false, 33)",
        null);
    interpreter.interpret(
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (4, 4.4, false, 44)",
        null);
    interpreter.interpret(
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (5, 5.5, false, 55)",
        null);
  }

  @After
  public void close() throws IOException, StorageEngineException {
    interpreter.close();
    EnvironmentUtils.cleanEnv();
  }


  @Test
  public void testNonQuery() {
    for (int i = 0; i < 100; i++) {
      String script = String
          .format("INSERT INTO root.test.wf02(timestamp,temperature) VALUES(%d,%f)", i,
              Math.random() * 10);
      InterpreterResult actual = interpreter.interpret(script, null);
      Assert.assertNotNull(actual);
      Assert.assertEquals(Code.SUCCESS, actual.code());
      Assert.assertEquals("Sql executed.", actual.message().get(0).getData());
    }
  }

  @Test
  public void testSelectColumnStatement() {
    InterpreterResult actual = interpreter
        .interpret("select status from root.test.wf01.wt01", null);
    String gt = "Time\troot.test.wf01.wt01.status\n"
        + "1\tfalse\n"
        + "2\ttrue\n"
        + "3\tfalse\n"
        + "4\tfalse\n"
        + "5\tfalse";
    Assert.assertNotNull(actual);
    Assert.assertEquals(Code.SUCCESS, actual.code());
    Assert.assertEquals(gt, actual.message().get(0).getData());
  }

  @Test
  public void testSelectColumnStatementWithTimeFilter() {
    InterpreterResult actual = interpreter
        .interpret("select * from root.test.wf01.wt01 where time > 2 and time < 6", null);
    String gt =
        "Time\troot.test.wf01.wt01.temperature\troot.test.wf01.wt01.status\troot.test.wf01.wt01.hardware\n"
            + "3\t3.3\tfalse\t33.0\n"
            + "4\t4.4\tfalse\t44.0\n"
            + "5\t5.5\tfalse\t55.0";
    Assert.assertNotNull(actual);
    Assert.assertEquals(Code.SUCCESS, actual.code());
    Assert.assertEquals(gt, actual.message().get(0).getData());
  }

  @Test
  public void testException() {
    InterpreterResult actual;
    String wrongSql;

    wrongSql = "select * from";
    actual = interpreter.interpret(wrongSql, null);
    Assert.assertNotNull(actual);
    Assert.assertEquals(Code.ERROR, actual.code());
    Assert.assertEquals(
        "StatementExecutionException: 401: meet error while parsing SQL to physical plan: {}line 1:13 missing ROOT at '<EOF>'",
        actual.message().get(0).getData());

    wrongSql = "select * from a";
    actual = interpreter.interpret(wrongSql, null);
    Assert.assertNotNull(actual);
    Assert.assertEquals(Code.ERROR, actual.code());
    Assert.assertEquals(
        "StatementExecutionException: 401: meet error while parsing SQL to physical plan: {}line 1:14 mismatched input 'a' expecting {FROM, ',', '.'}",
        actual.message().get(0).getData());

    wrongSql = "select * from root a";
    Assert.assertNotNull(actual);
    Assert.assertEquals(Code.ERROR, actual.code());
    Assert.assertEquals(
        "StatementExecutionException: 401: meet error while parsing SQL to physical plan: {}line 1:14 mismatched input 'a' expecting {FROM, ',', '.'}",
        actual.message().get(0).getData());

  }


  @Test
  public void TestMultiLines() {
    String insert = "SET STORAGE GROUP TO root.test.wf01.wt01;\n"
        + "CREATE TIMESERIES root.test.wf01.wt01.status WITH DATATYPE=BOOLEAN, ENCODING=PLAIN;\n"
        + "CREATE TIMESERIES root.test.wf01.wt01.temperature WITH DATATYPE=FLOAT, ENCODING=PLAIN;\n"
        + "CREATE TIMESERIES root.test.wf01.wt01.hardware WITH DATATYPE=INT32, ENCODING=PLAIN;\n"
        + "\n"
        + "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware)\n"
        + "VALUES (1, 1.1, false, 11);\n"
        + "\n"
        + "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware)\n"
        + "VALUES (2, 2.2, true, 22);\n"
        + "\n"
        + "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware)\n"
        + "VALUES (3, 3.3, false, 33);\n"
        + "\n"
        + "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware)\n"
        + "VALUES (4, 4.4, false, 44);\n"
        + "\n"
        + "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware)\n"
        + "VALUES (5, 5.5, false, 55);\n"
        + "\n"
        + "\n";
    String[] gt = new String[]{
        "SET STORAGE GROUP TO root.test.wf01.wt01",
        "CREATE TIMESERIES root.test.wf01.wt01.status WITH DATATYPE=BOOLEAN, ENCODING=PLAIN",
        "CREATE TIMESERIES root.test.wf01.wt01.temperature WITH DATATYPE=FLOAT, ENCODING=PLAIN",
        "CREATE TIMESERIES root.test.wf01.wt01.hardware WITH DATATYPE=INT32, ENCODING=PLAIN",
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (1, 1.1, false, 11)",
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (2, 2.2, true, 22)",
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (3, 3.3, false, 33)",
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (4, 4.4, false, 44)",
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (5, 5.5, false, 55)",
    };
    Assert.assertArrayEquals(gt, IoTDBInterpreter.parseMultiLinesSQL(insert));
  }

  @Test
  public void TestMultiLines2() {
    String query = "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware)\n"
        + "VALUES (4, 4.4, false, 44);\n"
        + "\n"
        + "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware)\n"
        + "VALUES (5, 5.5, false, 55);\n"
        + "\n"
        + "\n"
        + "SELECT *\n"
        + "FROM root.test.wf01.wt01\n"
        + "WHERE time >= 1\n"
        + "\tAND time <= 6;";

    String[] gt = new String[]{
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (4, 4.4, false, 44)",
        "INSERT INTO root.test.wf01.wt01 (timestamp, temperature, status, hardware) VALUES (5, 5.5, false, 55)",
        "SELECT * FROM root.test.wf01.wt01 WHERE time >= 1  AND time <= 6",
    };
    Assert.assertArrayEquals(gt, IoTDBInterpreter.parseMultiLinesSQL(query));
  }
}