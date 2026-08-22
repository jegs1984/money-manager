from decimal import Decimal, InvalidOperation

from django import template

register = template.Library()


@register.filter
def clp(value):
    """Render whole Chilean pesos consistently, e.g. ``$1.234.567``."""
    try:
        amount = Decimal(value).quantize(Decimal('1'))
    except (InvalidOperation, TypeError, ValueError):
        return '—'
    sign = '-' if amount < 0 else ''
    digits = f'{abs(amount):,.0f}'.replace(',', '.')
    return f'{sign}${digits}'


@register.filter
def get_item(mapping, key):
    return mapping.get(key) if mapping else None


@register.filter
def direction_label(value):
    return {'IN': 'Ingreso', 'OUT': 'Gasto'}.get(value, value)
