package lk.thuhina.water.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.sales.rules.Bottles;
import lk.thuhina.water.sales.rules.ExchangeRules;
import lk.thuhina.water.sales.rules.Problem;
import lk.thuhina.water.sales.rules.SaleCalc;
import lk.thuhina.water.sales.rules.SaleInputs.BottleInfo;
import lk.thuhina.water.sales.rules.SaleInputs.CustomerView;
import lk.thuhina.water.sales.rules.SaleInputs.Price;
import lk.thuhina.water.sales.rules.SaleLine;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M07 C4: the cases in {@code rules/exchange-cases.json} are shared with the client's TypeScript copy of ExchangeRules
 * (Enter Bills live result), so both give the same amounts, balances and messages.
 */
class ExchangeRulesSharedCasesTest {

    @TestFactory
    List<DynamicTest> sharedCases() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/rules/exchange-cases.json")) {
            root = JsonMapper.builder().build().readTree(in);
        }
        String name = root.get("customer").get("name").asString();
        String typeName = root.get("customer").get("typeName").asString();
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asString(), () -> {
                Map<String, Bottles> have = new LinkedHashMap<>();
                c.get("bottles").properties().forEach(e -> have.put(e.getKey(), bottles(e.getValue())));
                List<ExchangeRules.Line> lines = new ArrayList<>();
                for (JsonNode l : c.get("lines")) {
                    String code = l.get("bottle").asString();
                    JsonNode b = root.get("bottles").get(code);
                    Price price = l.get("price").isNull() ? null : new Price(l.get("price").decimalValue(), l.get("agreed").asBoolean());
                    lines.add(new ExchangeRules.Line(new BottleInfo(code, b.get("label").asString(), b.get("name").asString()),
                            l.get("filled").asInt(), l.get("empties").asInt(), price));
                }
                SaleCalc calc = ExchangeRules.calculate(new CustomerView(7L, name, typeName, have), c.get("leftAtDoor").asBoolean(), lines,
                        c.get("note").isNull() ? null : c.get("note").asString(), c.get("phoneConfirmed").asBoolean());

                JsonNode ex = c.get("expect");
                assertThat(calc.amount()).isEqualByComparingTo(ex.get("amount").decimalValue());
                Map<String, Bottles> after = new LinkedHashMap<>();
                ex.get("after").properties().forEach(e -> after.put(e.getKey(), bottles(e.getValue())));
                assertThat(calc.after()).isEqualTo(after);
                List<String> lineText = new ArrayList<>();
                for (JsonNode l : ex.get("lines")) {
                    lineText.add(l.get("bottle").asString() + " " + l.get("filled").asInt() + "/" + l.get("empties").asInt() + " owed +"
                            + l.get("owedAdded").asInt() + " −" + l.get("owedCleared").asInt() + " collect +" + l.get("toCollectAdded").asInt()
                            + " −" + l.get("toCollectCleared").asInt() + " " + l.get("lineAmount").decimalValue().stripTrailingZeros().toPlainString());
                }
                assertThat(calc.lines().stream().map(ExchangeRulesSharedCasesTest::text).toList()).isEqualTo(lineText);
                List<String> problems = new ArrayList<>();
                for (JsonNode p : ex.get("problems")) {
                    problems.add((p.get("field").isNull() ? null : p.get("field").asString()) + "|" + p.get("code").asString() + "|"
                            + p.get("message").asString());
                }
                assertThat(calc.problems().stream().map(ExchangeRulesSharedCasesTest::text).toList()).isEqualTo(problems);
            }));
        }
        assertThat(tests).hasSizeGreaterThanOrEqualTo(10);
        return tests;
    }

    private static Bottles bottles(JsonNode a) {
        return new Bottles(a.get(0).asInt(), a.get(1).asInt(), a.get(2).asInt());
    }

    private static String text(SaleLine l) {
        BigDecimal amount = l.getLineAmount();
        return l.getBottleTypeCode() + " " + l.getFilled() + "/" + l.getEmpties() + " owed +" + l.getOwedAdded() + " −" + l.getOwedCleared()
                + " collect +" + l.getToCollectAdded() + " −" + l.getToCollectCleared() + " " + amount.stripTrailingZeros().toPlainString();
    }

    private static String text(Problem p) {
        return p.field() + "|" + p.code() + "|" + p.message();
    }
}
