import unittest

from workbook import parse


class InitialQuantityTest(unittest.TestCase):
    def test_outbound_classification_preserves_other_column(self):
        rows = [
            ['类别', '品名', '资产数量', '北京', '横店', '《无尽夏》A组'] + [''] * 8 + ['其他出库'],
            ['摇臂', '测试设备', 4, 0, 0, 1] + [''] * 8 + ['无尽夏（灯）*2\n《无尽夏》B组*1'],
        ]
        colors = [[False] * len(row) for row in rows]
        result = parse([('Sheet1', rows, colors)], 'INITIAL', {})['rows'][0]['initial']['outbounds']
        self.assertEqual(['CREW', 'OTHER', 'OTHER'], [out['kind'] for out in result])
        self.assertEqual(['《无尽夏》A组', '无尽夏（灯）', '《无尽夏》B组'], [out['name'] for out in result])

    def parse_row(self, total, beijing, hengdian):
        rows = [
            ['类别', '品名', '资产数量', '北京', '横店'],
            ['摇臂类', '测试设备', total, beijing, hengdian],
        ]
        colors = [[False] * len(row) for row in rows]
        return parse([('Sheet1', rows, colors)], 'INITIAL', {})['rows'][0]

    def test_blank_branch_quantities_are_zero(self):
        for blank in (None, '', '   ', '——', '-'):
            with self.subTest(blank=blank):
                row = self.parse_row(0, blank, blank)
                self.assertEqual(0, row['initial']['beijing'])
                self.assertEqual(0, row['initial']['hengdian'])
                self.assertEqual([], row['parserIssues'])

    def test_filled_branch_quantity_is_preserved(self):
        row = self.parse_row(5, None, 5)
        self.assertEqual(5, row['initial']['hengdian'])
        self.assertEqual([], row['parserIssues'])

    def test_blank_branch_still_participates_in_difference(self):
        row = self.parse_row(5, '', None)
        self.assertIn('资产5，已分配0，差额5', row['parserIssues'])

    def test_missing_total_and_invalid_branch_require_review(self):
        for values in ((None, '', ''), (0, '待定', '')):
            with self.subTest(values=values):
                row = self.parse_row(*values)
                self.assertTrue(any('需补充核对' in issue for issue in row['parserIssues']))
                self.assertIsNone(row['initial']['total'] if values[0] is None else row['initial']['beijing'])

    def test_missing_total_keeps_distribution_and_pending_total(self):
        row = self.parse_row(None, 2, '')
        self.assertEqual(2, row['quantity'])
        self.assertIsNone(row['initial']['total'])


if __name__ == '__main__':
    unittest.main()
