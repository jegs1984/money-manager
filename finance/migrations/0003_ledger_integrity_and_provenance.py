from django.contrib.postgres.constraints import ExclusionConstraint
from django.contrib.postgres.fields import DateRangeField, RangeOperators
from django.db import migrations, models
import django.db.models.deletion


def verify_periods_do_not_overlap(apps, schema_editor):
    Period = apps.get_model('finance', 'Period')
    periods = list(Period.objects.order_by('start_date', 'end_date').values('id', 'name', 'start_date', 'end_date'))
    for previous, current in zip(periods, periods[1:]):
        if current['start_date'] <= previous['end_date']:
            raise RuntimeError(
                'Cannot enforce non-overlapping periods while '
                f'"{previous["name"]}" and "{current["name"]}" overlap. '
                'Resolve the dates manually, then rerun the migration.'
            )


class Migration(migrations.Migration):

    dependencies = [
        ('finance', '0002_importbatch_and_more'),
    ]

    operations = [
        migrations.RunPython(verify_periods_do_not_overlap, migrations.RunPython.noop),
        migrations.RemoveConstraint(
            model_name='budgetitem',
            name='finance_budget_item_period_category_unique',
        ),
        migrations.AddConstraint(
            model_name='budgetitem',
            constraint=models.UniqueConstraint(
                fields=('period', 'category', 'type'),
                name='finance_budget_item_period_category_type_unique',
            ),
        ),
        migrations.AddConstraint(
            model_name='period',
            constraint=ExclusionConstraint(
                name='finance_period_dates_do_not_overlap',
                expressions=[
                    (
                        models.Func(
                            'start_date', 'end_date', models.Value('[]'),
                            function='DATERANGE', output_field=DateRangeField(),
                        ),
                        RangeOperators.OVERLAPS,
                    ),
                ],
            ),
        ),
        migrations.AddField(
            model_name='transaction',
            name='source_fingerprint',
            field=models.CharField(blank=True, db_index=True, max_length=160),
        ),
        migrations.AddField(
            model_name='transaction',
            name='source_staging_cc_transaction',
            field=models.OneToOneField(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.PROTECT,
                related_name='committed_transaction',
                to='finance.stagingcctransaction',
            ),
        ),
        migrations.AddField(
            model_name='transaction',
            name='source_staging_transaction',
            field=models.OneToOneField(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.PROTECT,
                related_name='committed_transaction',
                to='finance.stagingtransaction',
            ),
        ),
        migrations.AddConstraint(
            model_name='transaction',
            constraint=models.UniqueConstraint(
                condition=~models.Q(source_fingerprint=''),
                fields=('source_fingerprint',),
                name='finance_transaction_source_fingerprint_unique',
            ),
        ),
    ]
