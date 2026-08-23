from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [('finance', '0005_goal_recurringplan_merchantrule_and_more')]

    operations = [
        migrations.CreateModel(
            name='InstallmentObligation',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('description', models.CharField(max_length=255)),
                ('next_due_date', models.DateField()),
                ('remaining_installments', models.PositiveSmallIntegerField()),
                ('installment_value', models.DecimalField(decimal_places=2, max_digits=14)),
                ('remaining_amount', models.DecimalField(decimal_places=2, max_digits=14)),
                ('is_complete', models.BooleanField(default=False)),
                ('category', models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name='installment_obligations', to='finance.category')),
                ('source_transaction', models.OneToOneField(on_delete=django.db.models.deletion.PROTECT, related_name='installment_obligation', to='finance.transaction')),
            ],
            options={'db_table': 'finance_installment_obligation', 'ordering': ['is_complete', 'next_due_date', 'description']},
        ),
        migrations.AddConstraint(model_name='installmentobligation', constraint=models.CheckConstraint(check=models.Q(('remaining_installments__gte', 0)), name='finance_installment_remaining_gte_0')),
        migrations.AddConstraint(model_name='installmentobligation', constraint=models.CheckConstraint(check=models.Q(('installment_value__gt', 0)), name='finance_installment_value_gt_0')),
        migrations.AddConstraint(model_name='installmentobligation', constraint=models.CheckConstraint(check=models.Q(('remaining_amount__gte', 0)), name='finance_installment_amount_gte_0')),
    ]
