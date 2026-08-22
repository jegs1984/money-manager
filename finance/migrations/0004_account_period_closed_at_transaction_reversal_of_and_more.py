from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('finance', '0003_ledger_integrity_and_provenance'),
    ]

    operations = [
        migrations.CreateModel(
            name='Account',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('name', models.CharField(max_length=100, unique=True)),
                ('kind', models.CharField(choices=[('CHECKING', 'Checking account'), ('SAVINGS', 'Savings account'), ('CASH', 'Cash'), ('CREDIT_CARD', 'Credit card')], max_length=20)),
                ('external_reference', models.CharField(blank=True, max_length=100)),
                ('opening_balance', models.DecimalField(decimal_places=2, default=0, max_digits=14)),
                ('is_active', models.BooleanField(default=True)),
            ],
            options={'db_table': 'finance_account', 'ordering': ['name']},
        ),
        migrations.AddField(
            model_name='period',
            name='closed_at',
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name='transaction',
            name='reversal_of',
            field=models.OneToOneField(blank=True, null=True, on_delete=django.db.models.deletion.PROTECT, related_name='reversal', to='finance.transaction'),
        ),
        migrations.CreateModel(
            name='Transfer',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('date', models.DateField()),
                ('amount', models.DecimalField(decimal_places=2, max_digits=14)),
                ('description', models.CharField(blank=True, max_length=255)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('destination_account', models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name='incoming_transfers', to='finance.account')),
                ('source_account', models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name='outgoing_transfers', to='finance.account')),
            ],
            options={'db_table': 'finance_transfer', 'ordering': ['-date', '-id']},
        ),
        migrations.CreateModel(
            name='Reconciliation',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('statement_date', models.DateField()),
                ('statement_balance', models.DecimalField(decimal_places=2, max_digits=14)),
                ('calculated_balance', models.DecimalField(decimal_places=2, max_digits=14)),
                ('notes', models.TextField(blank=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('account', models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name='reconciliations', to='finance.account')),
            ],
            options={'db_table': 'finance_reconciliation', 'ordering': ['-statement_date', '-id']},
        ),
        migrations.AddConstraint(
            model_name='account',
            constraint=models.UniqueConstraint(condition=~models.Q(external_reference=''), fields=('external_reference',), name='finance_account_external_reference_unique'),
        ),
        migrations.AddField(
            model_name='transaction',
            name='account',
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name='transactions', to='finance.account'),
        ),
        migrations.AddConstraint(
            model_name='transfer',
            constraint=models.CheckConstraint(check=models.Q(amount__gt=0), name='finance_transfer_amount_gt_0'),
        ),
        migrations.AddConstraint(
            model_name='reconciliation',
            constraint=models.UniqueConstraint(fields=('account', 'statement_date'), name='finance_reconciliation_account_date_unique'),
        ),
    ]
