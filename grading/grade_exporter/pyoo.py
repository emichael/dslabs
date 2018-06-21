"""
PyOO - Pythonic interface to Apache OpenOffice API (UNO)

Copyright (c) 2016 Seznam.cz, a.s.

"""

from __future__ import division

import datetime
import functools
import itertools
import numbers
import os
import sys

import uno


# Filters used when saving document.
FILTER_PDF_EXPORT = 'writer_pdf_Export'
FILTER_EXCEL_97 = 'MS Excel 97'
FILTER_EXCEL_2007 = 'Calc MS Excel 2007 XML'

# Number format choices
FORMAT_TEXT = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.TEXT')
FORMAT_INT = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.NUMBER_INT')
FORMAT_FLOAT = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.NUMBER_DEC2')
FORMAT_INT_SEP = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.NUMBER_1000INT')
FORMAT_FLOAT_SEP = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.NUMBER_1000DEC2')
FORMAT_PERCENT_INT = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.PERCENT_INT')
FORMAT_PERCENT_FLOAT = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.PERCENT_DEC2')
FORMAT_DATE = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.DATE_SYSTEM_SHORT')
FORMAT_TIME = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.TIME_HHMM')
FORMAT_DATETIME = uno.getConstantByName('com.sun.star.i18n.NumberFormatIndex.DATETIME_SYSTEM_SHORT_HHMM')

# Font weight choices
FONT_WEIGHT_DONTKNOW = uno.getConstantByName('com.sun.star.awt.FontWeight.DONTKNOW')
FONT_WEIGHT_THIN = uno.getConstantByName('com.sun.star.awt.FontWeight.THIN')
FONT_WEIGHT_ULTRALIGHT = uno.getConstantByName('com.sun.star.awt.FontWeight.ULTRALIGHT')
FONT_WEIGHT_LIGHT = uno.getConstantByName('com.sun.star.awt.FontWeight.LIGHT')
FONT_WEIGHT_SEMILIGHT = uno.getConstantByName('com.sun.star.awt.FontWeight.SEMILIGHT')
FONT_WEIGHT_NORMAL = uno.getConstantByName('com.sun.star.awt.FontWeight.NORMAL')
FONT_WEIGHT_SEMIBOLD = uno.getConstantByName('com.sun.star.awt.FontWeight.SEMIBOLD')
FONT_WEIGHT_BOLD = uno.getConstantByName('com.sun.star.awt.FontWeight.BOLD')
FONT_WEIGHT_ULTRABOLD = uno.getConstantByName('com.sun.star.awt.FontWeight.ULTRABOLD')
FONT_WEIGHT_BLACK = uno.getConstantByName('com.sun.star.awt.FontWeight.BLACK')

# Text underline choices (only first three are present here)
UNDERLINE_NONE = uno.getConstantByName('com.sun.star.awt.FontUnderline.NONE')
UNDERLINE_SINGLE = uno.getConstantByName('com.sun.star.awt.FontUnderline.SINGLE')
UNDERLINE_DOUBLE = uno.getConstantByName('com.sun.star.awt.FontUnderline.DOUBLE')


# Text alignment choices
TEXT_ALIGN_STANDARD = 'STANDARD'
TEXT_ALIGN_LEFT = 'LEFT'
TEXT_ALIGN_CENTER = 'CENTER'
TEXT_ALIGN_RIGHT = 'RIGHT'
TEXT_ALIGN_BLOCK = 'BLOCK'
TEXT_ALIGN_REPEAT = 'REPEAT'

# Axis choices
AXIS_PRIMARY = uno.getConstantByName('com.sun.star.chart.ChartAxisAssign.PRIMARY_Y')
AXIS_SECONDARY = uno.getConstantByName('com.sun.star.chart.ChartAxisAssign.SECONDARY_Y')

# Exceptions thrown by UNO.
# We try to catch them and re-throw Python standard exceptions.
_IndexOutOfBoundsException = uno.getClass('com.sun.star.lang.IndexOutOfBoundsException')
_NoSuchElementException = uno.getClass('com.sun.star.container.NoSuchElementException')
_IOException = uno.getClass('com.sun.star.io.IOException')

_NoConnectException = uno.getClass('com.sun.star.connection.NoConnectException')
_ConnectionSetupException = uno.getClass('com.sun.star.connection.ConnectionSetupException')


UnoException = uno.getClass('com.sun.star.uno.Exception')


PY2 = sys.version_info[0] == 2
PY3 = sys.version_info[0] == 3

if PY3:
    string_types = str,
    integer_types = int,
    text_type = str
else:
    string_types = basestring,
    integer_types = (int, long)
    text_type = unicode
    range = xrange


def str_repr(klass):
    """
    Implements string conversion methods for the given class.

    The given class must implement the __str__ method. This decorat
    will add __repr__ and __unicode__ (for Python 2).

    """
    if PY2:
        klass.__unicode__ = klass.__str__
        klass.__str__ = lambda self: self.__unicode__().encode('utf-8')
    klass.__repr__ = lambda self: '<%s: %r>' % (self.__class__.__name__, str(self))
    return klass


def _clean_slice(key, length):
    """
    Validates and normalizes a cell range slice.

    >>> _clean_slice(slice(None, None), 10)
    (0, 10)
    >>> _clean_slice(slice(-10, 10), 10)
    (0, 10)
    >>> _clean_slice(slice(-11, 11), 10)
    (0, 10)
    >>> _clean_slice(slice('x', 'y'), 10)
    Traceback (most recent call last):
    ...
    TypeError: Cell indices must be integers, str given.
    >>> _clean_slice(slice(0, 10, 2), 10)
    Traceback (most recent call last):
    ...
    NotImplementedError: Cell slice with step is not supported.
    >>> _clean_slice(slice(5, 5), 10)
    Traceback (most recent call last):
    ...
    ValueError: Cell slice can not be empty.

    """
    if key.step is not None:
        raise NotImplementedError('Cell slice with step is not supported.')
    start, stop = key.start, key.stop
    if start is None:
        start = 0
    if stop is None:
        stop = length
    if not isinstance(start, integer_types):
        raise TypeError('Cell indices must be integers, %s given.' % type(start).__name__)
    if not isinstance(stop, integer_types):
        raise TypeError('Cell indices must be integers, %s given.' % type(stop).__name__)
    if start < 0:
        start = start + length
    if stop < 0:
        stop = stop + length
    start, stop = max(0, start), min(length, stop)
    if start == stop:
        raise ValueError('Cell slice can not be empty.')
    return start, stop


def _clean_index(key, length):
    """
    Validates and normalizes a cell range index.

    >>> _clean_index(0, 10)
    0
    >>> _clean_index(-10, 10)
    0
    >>> _clean_index(10, 10)
    Traceback (most recent call last):
    ...
    IndexError: Cell index out of range.
    >>> _clean_index(-11, 10)
    Traceback (most recent call last):
    ...
    IndexError: Cell index out of range.
    >>> _clean_index(None, 10)
    Traceback (most recent call last):
    ...
    TypeError: Cell indices must be integers, NoneType given.

    """
    if not isinstance(key, integer_types):
        raise TypeError('Cell indices must be integers, %s given.' % type(key).__name__)
    if -length <= key < 0:
        return key + length
    elif 0 <= key < length:
        return key
    else:
        raise IndexError('Cell index out of range.')


def _row_name(index):
    """
    Converts a row index to a row name.

    >>> _row_name(0)
    '1'
    >>> _row_name(10)
    '11'

    """
    return '%d' % (index + 1)


def _col_name(index):
    """
    Converts a column index to a column name.

    >>> _col_name(0)
    'A'
    >>> _col_name(26)
    'AA'

    """
    for exp in itertools.count(1):
        limit = 26 ** exp
        if index < limit:
            return ''.join(chr(ord('A') + index // (26 ** i) % 26) for i in range(exp-1, -1, -1))
        index -= limit


@str_repr
class SheetPosition(object):
    """
    Position of a rectangular are in a spreadsheet.

    This class represent physical position in 100/th mm,
    see SheetAddress class for a logical address of cells.

    >>> position = SheetPosition(1000, 2000)
    >>> print position
    x=1000, y=2000
    >>> position = SheetPosition(1000, 2000, 3000, 4000)
    >>> print position
    x=1000, y=2000, width=3000, height=4000

    """

    __slots__ = ('x', 'y', 'width', 'height')

    def __init__(self, x, y, width=0, height=0):
        self.x = x
        self.y = y
        self.width = width
        self.height = height

    def __str__(self):
        if self.width == self.height == 0:
            return u'x=%d, y=%d' % (self.x, self.y)
        return u'x=%d, y=%d, width=%d, height=%d' % (self.x, self.y,
                                                    self.width, self.height)

    def replace(self, x=None, y=None, width=None, height=None):
        x = x if x is not None else self.x
        y = y if y is not None else self.y
        width = width if width is not None else self.width
        height = height if height is not None else self.height
        return self.__class__(x, y, width, height)

    @classmethod
    def _from_uno(cls, position, size):
        return cls(position.X, position.Y, size.Width, size.Height)

    def _to_uno(self):
        struct = uno.createUnoStruct('com.sun.star.awt.Rectangle')
        struct.X = self.x
        struct.Y = self.y
        struct.Width = self.width
        struct.Height = self.height
        return struct

@str_repr
class SheetAddress(object):
    """
    Address of a cell or a rectangular range of cells in a spreadsheet.

    This class represent logical address of cells, see SheetPosition
    class for physical location.

    >>> address = SheetAddress(1, 2)
    >>> print address
    $C$2
    >>> address = SheetAddress(1, 2, 3, 4)
    >>> print address
    $C$2:$F$4

    """

    __slots__ = ('row', 'col', 'row_count', 'col_count')

    def __init__(self, row, col, row_count=1, col_count=1):
        self.row, self.col = row, col
        self.row_count, self.col_count = row_count, col_count

    def __str__(self):
        return self.formula(row_abs=True, col_abs=True)

    @property
    def row_end(self):
        return self.row + self.row_count - 1

    @property
    def col_end(self):
        return self.col + self.col_count - 1

    def formula(self, row_abs=False, col_abs=False):
        """
        Returns this address as a string to be used in formulas.
        """
        if row_abs and col_abs:
            fmt = u'$%s$%s'
        elif row_abs:
            fmt = u'%s$%s'
        elif col_abs:
            fmt = u'$%s%s'
        else:
            fmt = u'%s%s'
        start = fmt % (_col_name(self.col), _row_name(self.row))
        if self.row_count == self.col_count == 1:
            return start
        end = fmt % (_col_name(self.col_end), _row_name(self.row_end))
        return '%s:%s' % (start, end)

    def replace(self, row=None, col=None, row_count=None, col_count=None):
        """
        Returns a new address which the specified fields replaced.
        """
        row = row if row is not None else self.row
        col = col if col is not None else self.col
        row_count = row_count if row_count is not None else self.row_count
        col_count = col_count if col_count is not None else self.col_count
        return self.__class__(row, col, row_count, col_count)

    @classmethod
    def _from_uno(cls, target):
        row_count = target.EndRow - target.StartRow + 1
        col_count = target.EndColumn - target.StartColumn + 1
        return cls(target.StartRow, target.StartColumn, row_count, col_count)

    def _to_uno(self, sheet):
        struct = uno.createUnoStruct('com.sun.star.table.CellRangeAddress')
        struct.Sheet = sheet
        struct.StartColumn = self.col
        struct.StartRow = self.row
        struct.EndColumn = self.col_end
        struct.EndRow = self.row_end
        return struct


class _UnoProxy(object):
    """
    Abstract base class for objects which act as a proxy to UNO objects.
    """
    __slots__ = ('_target',)

    def __init__(self, target):
        self._target = target

    def __repr__(self):
        return '<%s: %r>' % (self.__class__.__name__,
                             self._target.getSupportedServiceNames())


class NamedCollection(_UnoProxy):
    """
    Base class for collections accessible by both index and name.
    """

    # Target must implement both of:
    # http://www.openoffice.org/api/docs/common/ref/com/sun/star/container/XIndexAccess.html
    # http://www.openoffice.org/api/docs/common/ref/com/sun/star/container/XNameAccess.html

    __slots__ = ()

    def __len__(self):
        return self._target.getCount()

    def __getitem__(self, key):
        if isinstance(key, integer_types):
            target = self._get_by_index(key)
            return self._factory(target)
        if isinstance(key, string_types):
            target = self._get_by_name(key)
            return self._factory(target)
        raise TypeError('%s must be accessed either by index or name.'
                        % self.__class__.__name__)

    # Internal:

    def _factory(self, target):
        raise NotImplementedError # pragma: no cover

    def _get_by_index(self, index):
        try:
            # http://www.openoffice.org/api/docs/common/ref/com/sun/star/container/XIndexAccess.html#getByIndex
            return self._target.getByIndex(index)
        except _IndexOutOfBoundsException:
            raise IndexError(index)

    def _get_by_name(self, name):
        try:
            # http://www.openoffice.org/api/docs/common/ref/com/sun/star/container/XNameAccess.html#getByName
            return self._target.getByName(name)
        except _NoSuchElementException:
            raise KeyError(name)


class DiagramSeries(_UnoProxy):
    """
    Diagram series.

    This class allows to control how one sequence of values (typically
    one table column) is displayed in a chart (for example appearance
    of one line).

    """

    __slots__ = ()

    def __get_axis(self):
        """
        Gets to which axis this series are assigned.
        """
        return self._target.getPropertyValue('Axis')
    def __set_axis(self, value):
        """
        Sets to which axis this series are assigned.
        """
        self._target.setPropertyValue('Axis', value)
    axis = property(__get_axis, __set_axis)

    def __get_line_color(self):
        """
        Gets line color.
        """
        return self._target.getPropertyValue('LineColor')
    def __set_line_color(self, value):
        """
        Sets line color.

        Be aware that this call is sometimes ignored by OpenOffice.
        """
        self._target.setPropertyValue('LineColor', value)
    line_color = property(__get_line_color, __set_line_color)

    def __get_fill_color(self):
        """
        Gets fill color.
        """
        return self._target.getPropertyValue('FillColor')
    def __set_fill_color(self, value):
        """
        Sets fill color.
        """
        self._target.setPropertyValue('FillColor', value)
    fill_color = property(__get_fill_color, __set_fill_color)


class DiagramSeriesCollection(_UnoProxy):
    """
    Provides access to individual diagram series.

    Instance of this class is returned when series property of
    the Diagram class is accessed.
    """

    __slots__ = ()

    # It seems that length of series can not be easily determined so
    # here is no __len__ method.

    def __getitem__(self, key):
        try:
            target = self._target.getDataRowProperties(key)
        except _IndexOutOfBoundsException:
            raise IndexError(key)
        else:
            return DiagramSeries(target)


class Axis(_UnoProxy):
    """
    Chart axis
    """

    __slots__ = ()

    def __get_visible(self):
        """
        Gets whether this axis is visible.
        """
        # Getting target.HasXAxis is a lot of faster then accessing
        # target.XAxis.Visible property.
        return self._target.getPropertyValue(self._has_axis_property)
    def __set_visible(self, value):
        """
        Sets whether this axis is visible.
        """
        return self._target.setPropertyValue(self._has_axis_property, value)
    visible = property(__get_visible, __set_visible)

    def __get_title(self):
        """
        Gets title of this axis.
        """
        target = self._get_title_target()
        return target.getPropertyValue('String')
    def __set_title(self, value):
        """
        Sets title of this axis.
        """
        # OpenOffice on Debian "squeeze" ignore value of target.XAxis.String
        # unless target.HasXAxisTitle is set to True first. (Despite the
        # fact that target.HasXAxisTitle is reported to be False until
        # target.XAxis.String is set to non empty value.)
        self._target.setPropertyValue(self._has_axis_title_property, True)
        target = self._get_title_target()
        target.setPropertyValue('String', text_type(value))
    title = property(__get_title, __set_title)

    def __get_logarithmic(self):
        """
        Gets whether this axis has an logarithmic scale.
        """
        target = self._get_axis_target()
        return target.getPropertyValue('Logarithmic')
    def __set_logarithmic(self, value):
        """
        Sets whether this axis has an logarithmic scale.
        """
        target = self._get_axis_target()
        target.setPropertyValue('Logarithmic', value)
    logarithmic = property(__get_logarithmic, __set_logarithmic)

    def __get_reversed(self):
        """
        Gets whether this axis is reversed
        """
        target = self._get_axis_target()
        return target.getPropertyValue('ReverseDirection')
    def __set_reversed(self, value):
        """
        Sets whether this axis is reversed
        """
        target = self._get_axis_target()
        return target.setPropertyValue('ReverseDirection', value)
    reversed = property(__get_reversed, __set_reversed)


    # The _target property of this class does not hold the axis itself but
    # the owner diagram instance. So following methods and properties has
    # to be overridden in order to access appropriate UNO objects.

    _has_axis_property = None
    _has_axis_title_property = None

    def _get_axis_target(self):
        raise NotImplementedError # pragma: no cover

    def _get_title_target(self):
        raise NotImplementedError # pragma: no cover


class XAxis(Axis):

    __slots__ = ()

    _has_axis_property = 'HasXAxis'
    _has_axis_title_property = 'HasXAxisTitle'

    def _get_axis_target(self):
        return self._target.getXAxis()

    def _get_title_target(self):
        return self._target.getXAxisTitle()


class YAxis(Axis):

    __slots__ = ()

    _has_axis_property = 'HasYAxis'
    _has_axis_title_property = 'HasYAxisTitle'

    def _get_axis_target(self):
        return self._target.getYAxis()

    def _get_title_target(self):
        return self._target.getYAxisTitle()


class SecondaryXAxis(Axis):

    __slots__ = ()

    _has_axis_property = 'HasSecondaryXAxis'
    _has_axis_title_property = 'HasSecondaryXAxisTitle'

    def _get_axis_target(self):
        return self._target.getSecondaryXAxis()

    def _get_title_target(self):
        return self._target.getSecondXAxisTitle()


class SecondaryYAxis(Axis):

    __slots__ = ()

    _has_axis_property = 'HasSecondaryYAxis'
    _has_axis_title_property = 'HasSecondaryYAxisTitle'

    def _get_axis_target(self):
        return self._target.getSecondaryYAxis()

    def _get_title_target(self):
        return self._target.getSecondYAxisTitle()


class Diagram(_UnoProxy):
    """
    Diagram - inner content of a chart.

    Each chart has a diagram which specifies how data are rendered.
    The inner diagram can be changed or replaced while the
    the outer chart instance is still the same.

    """

    __slots__ = ()

    @property
    def series(self):
        """
        Collection of diagram series.
        """
        return DiagramSeriesCollection(self._target)

    # Following code is specific to 2D diagrams. If support for another
    # diagram types is added (e.g. pie) then a new class should
    # be probably introduced.

    @property
    def x_axis(self):
        """
        X (bottom) axis
        """
        return XAxis(self._target)

    @property
    def y_axis(self):
        """
        Y (left) axis
        """
        return YAxis(self._target)

    @property
    def secondary_x_axis(self):
        """
        Secondary X (top) axis
        """
        return SecondaryXAxis(self._target)

    @property
    def secondary_y_axis(self):
        """
        Secondary Y (right) axis
        """
        return SecondaryYAxis(self._target)

    def __get_is_stacked(self):
        """
        Gets whether series of the diagram are stacked.
        """
        return self._target.getPropertyValue('Stacked')
    def __set_is_stacked(self, value):
        """
        Sets whether series of the diagram are stacked.
        """
        self._target.setPropertyValue('Stacked', value)
    is_stacked = property(__get_is_stacked, __set_is_stacked)


class BarDiagram(Diagram):
    """
    Bar or column diagram.

    Type of diagram can be changed using Chart.change_type method.
    """

    __slots__ = ()

    _type = 'com.sun.star.chart.BarDiagram'

    def __get_lines(self):
        """
        Gets count of series which are rendered as lines instead of lines.
        """
        return self._target.getPropertyValue('NumberOfLines')
    def __set_lines(self, value):
        """
        Sets count of series which are rendered as lines instead of lines
        """
        return self._target.setPropertyValue('NumberOfLines', value)
    lines = property(__get_lines, __set_lines)

    def __get_is_horizontal(self):
        """
        Gets whether this diagram is rendered with horizontal bars.

        If value is False then you get vertical columns.
        """
        # Be aware - this call is translated to UNO "Vertical" property.
        #
        # UNO API claims that if vertical is false then we get a column chart
        # rather than a bar chart -- which describes OpenOffice behavior.
        #
        # But the words "horizontal" and "vertical" simply mean opposite
        # of the UNO semantics. If you don't believe me then try to google
        # for "horizontal bar chart" and "vertical bar chart" images.
        return self._target.getPropertyValue('Vertical')
    def __set_is_horizontal(self, value):
        """
        Sets whether this diagram is rendered with horizontal bars.
        """
        return self._target.setPropertyValue('Vertical', value)
    is_horizontal = property(__get_is_horizontal, __set_is_horizontal)

    def __get_is_grouped(self):
        """
        Gets whether to group columns attached to different axis.

        If bars of a bar or column chart are attached to different axis,
        this property determines how to display those. If true, the bars
        are grouped together in one block for each axis, thus they are
        painted one group over the other.
        """
        return self._target.getPropertyValue('GroupBarsPerAxis')
    def __set_is_grouped(self, value):
        """
        Sets whether to group columns attached to different axis.
        """
        return self._target.setPropertyValue('GroupBarsPerAxis', value)
    is_grouped = property(__get_is_grouped, __set_is_grouped)


class LineDiagram(Diagram):
    """
    Line, spline or symbol diagram.

    Type of diagram can be changed using Chart.change_type method.
    """

    __slots__ = ()

    _type = 'com.sun.star.chart.LineDiagram'

    def __get_spline(self):
        return self._target.getPropertyValue('SplineType')
    def __set_spline(self, value):
        self._target.setPropertyValue('SplineType', int(value))
    spline = property(__get_spline, __set_spline)


# Registry of supported diagram types.
_DIAGRAM_TYPES = {
    BarDiagram._type: BarDiagram,
    LineDiagram._type: LineDiagram,
}


class Chart(_UnoProxy):
    """
    Chart
    """

    __slots__ = ('sheet', '_embedded')

    def __init__(self, sheet, target):
        self.sheet = sheet
        # Embedded object provides most of the functionality it will be
        # probably often needed -- cache it for better performance.
        self._embedded = target.getEmbeddedObject()
        super(Chart, self).__init__(target)

    @property
    def name(self):
        """
        Chart name which can be used as a key for accessing this chart.
        """
        return self._target.getName()

    @property
    def has_row_header(self):
        """
        Returns whether the first row is used for header
        """
        return self._target.getHasRowHeaders()

    @property
    def has_col_header(self):
        """
        Returns whether the first column is used for header
        """
        return self._target.getHasColumnHeaders()

    @property
    def ranges(self):
        """
        Returns a list of addresses with source data.
        """
        ranges = self._target.getRanges()
        return map(SheetAddress._from_uno, ranges)

    @property
    def diagram(self):
        """
        Diagram - inner content of this chart.

        The diagram can be replaced by another type using change_type method.
        """
        target = self._embedded.getDiagram()
        target_type = target.getDiagramType()
        cls = _DIAGRAM_TYPES.get(target_type, Diagram)
        return cls(target)

    def change_type(self, cls):
        """
        Change type of diagram in this chart.

        Accepts one of classes which extend Diagram.
        """
        target_type = cls._type
        target = self._embedded.createInstance(target_type)
        self._embedded.setDiagram(target)
        return cls(target)


class ChartCollection(NamedCollection):
    """
    Collection of charts in one sheet.
    """

    __slots__ = ('sheet',)

    def __init__(self, sheet, target):
        self.sheet = sheet
        super(ChartCollection, self).__init__(target)

    def __delitem__(self, key):
        if not isinstance(key, string_types):
            key = self[key].name
        self._delete(key)

    def create(self, name, position, ranges=(), col_header=False, row_header=False):
        """
        Creates and inserts a new chart.
        """
        rect = self._uno_rect(position)
        ranges = self._uno_ranges(ranges)
        self._create(name, rect, ranges, col_header, row_header)
        return self[name]

    # Internal:

    def _factory(self, target):
        return Chart(self.sheet, target)

    def _uno_rect(self, position):
        if isinstance(position, CellRange):
            position = position.position
        return position._to_uno()

    def _uno_ranges(self, ranges):
        if not isinstance(ranges, (list, tuple)):
            ranges = [ranges]
        return tuple(map(self._uno_range, ranges))

    def _uno_range(self, address):
        if isinstance(address, CellRange):
            address = address.address
        return address._to_uno(self.sheet.index)

    def _create(self, name, rect, ranges, col_header, row_header):
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/table/XTableCharts.html#addNewByName
        self._target.addNewByName(name, rect, ranges, col_header, row_header)

    def _delete(self, name):
        try:
            self._target.removeByName(name)
        except _NoSuchElementException:
            raise KeyError(name)


class SheetCursor(_UnoProxy):
    """
    Cursor in spreadsheet sheet.

    Most of spreadsheet operations are done using this cursor
    because cursor movement is much faster then cell range selection.

    """

    __slots__ = ('row', 'col', 'row_count', 'col_count',
                 'max_row_count', 'max_col_count')

    def __init__(self, target):
        # Target must be com.sun.star.sheet.XSheetCellCursor
        ra = target.getRangeAddress()
        self.row = 0
        self.col = 0
        self.row_count = ra.EndRow + 1
        self.col_count = ra.EndColumn + 1
        # Default cursor contains all the cells.
        self.max_row_count = self.row_count
        self.max_col_count = self.col_count
        super(SheetCursor, self).__init__(target)

    def get_target(self, row, col, row_count, col_count):
        """
        Moves cursor to the specified position and returns in.
        """
        # This method is called for almost any operation so it should
        # be maximally optimized.
        #
        # Any comparison here is negligible compared to UNO call. So we do all
        # possible checks which can prevent an unnecessary cursor movement.
        #
        # Generally we need to expand or collapse selection to the desired
        # size and move it to the desired position. But both of these actions
        # can fail if there is not enough space. For this reason we must
        # determine which of the actions has to be done first. In some cases
        # we must even move the cursor twice (cursor movement is faster than
        # selection change).
        #
        target = self._target
        # If we cannot resize selection now then we must move cursor first.
        if self.row + row_count > self.max_row_count or self.col + col_count > self.max_col_count:
            # Move cursor to the desired position if possible.
            row_delta = row - self.row if row + self.row_count <= self.max_row_count else 0
            col_delta = col - self.col if col + self.col_count <= self.max_col_count else 0
            target.gotoOffset(col_delta, row_delta)
            self.row += row_delta
            self.col += col_delta
        # Resize selection
        if (row_count, col_count) != (self.row_count, self.col_count):
            target.collapseToSize(col_count, row_count)
            self.row_count = row_count
            self.col_count = col_count
        # Move cursor to the desired position
        if (row, col) != (self.row, self.col):
            target.gotoOffset(col - self.col, row - self.row)
            self.row = row
            self.col = col
        return target


@str_repr
class CellRange(object):
    """
    Range of cells in one sheet.

    This is an abstract base class implements cell manipulation functionality.

    """
    # Does not extend _UnoProxy because it uses sheet cursor internally
    # instead of direct reference to UNO object.

    __slots__ = ('sheet', 'address')

    def __init__(self, sheet, address):
        self.sheet = sheet
        self.address = address

    def __str__(self):
        return text_type(self.address)

    @property
    def position(self):
        """
        Physical position of this cells.
        """
        target = self._get_target()
        position, size = target.getPropertyValues(('Position', 'Size'))
        return SheetPosition._from_uno(position, size)

    def __get_is_merged(self):
        """
        Gets whether cells are merged.
        """
        return self._get_target().getIsMerged()
    def __set_is_merged(self, value):
        """
        Sets whether cells are merged.
        """
        self._get_target().merge(value)
    is_merged = property(__get_is_merged, __set_is_merged)

    def __get_number_format(self):
        """
        Gets format of numbers in this cells.
        """
        return self._get_target().getPropertyValue('NumberFormat')
    def __set_number_format(self, value):
        """
        Sets format of numbers in this cells.
        """
        self._get_target().setPropertyValue('NumberFormat', value)
    number_format = property(__get_number_format, __set_number_format)

    def __get_text_align(self):
        """
        Gets horizontal alignment.

        Returns one of TEXT_ALIGN_* constants.
        """
        return self._get_target().getPropertyValue('HoriJustify').value
    def __set_text_align(self, value):
        """
        Sets horizontal alignment.

        Accepts TEXT_ALIGN_* constants.
        """
        # The HoriJustify property contains is a struct.
        # We need to get it, update value and then set it back.
        target = self._get_target()
        struct = target.getPropertyValue('HoriJustify')
        struct.value = value
        target.setPropertyValue('HoriJustify', struct)
    text_align = property(__get_text_align, __set_text_align)

    def __get_font_size(self):
        """
        Gets font size.
        """
        return self._get_target().getPropertyValue('CharHeight')
    def __set_font_size(self, value):
        """
        Sets font size.
        """
        return self._get_target().setPropertyValue('CharHeight', value)
    font_size = property(__get_font_size, __set_font_size)

    def __get_font_weight(self):
        """
        Gets font weight.
        """
        return self._get_target().getPropertyValue('CharWeight')
    def __set_font_weight(self, value):
        """
        Sets font weight.
        """
        return self._get_target().setPropertyValue('CharWeight', value)
    font_weight = property(__get_font_weight, __set_font_weight)

    def __get_underline(self):
        """
        Gets text underline.

        Returns UNDERLINE_* constants.
        """
        return self._get_target().getPropertyValue('CharUnderline')
    def __set_underline(self, value):
        """
        Sets text weight.

        Accepts UNDERLINE_* constants.
        """
        return self._get_target().setPropertyValue('CharUnderline', value)
    underline = property(__get_underline, __set_underline)

    def __get_text_color(self):
        """
        Gets text color.

        Color is returned as integer in format 0xAARRGGBB.
        Returns None if no the text color is not set.
        """
        value = self._get_target().getPropertyValue('CharColor')
        if value == -1:
            value = None
        return value
    def __set_text_color(self, value):
        """
        Sets text color.

        Color should be given as an integer in format 0xAARRGGBB.
        Unsets the text color if None value is given.
        """
        if value is None:
            value = -1
        return self._get_target().setPropertyValue('CharColor', value)
    text_color = property(__get_text_color, __set_text_color)

    def __get_background_color(self):
        """
        Gets cell background color.

        Color is returned as integer in format 0xAARRGGBB.
        Returns None if the background color is not set.
        """
        value = self._get_target().getPropertyValue('CellBackColor')
        if value == -1:
            value = None
        return value
    def __set_background_color(self, value):
        """
        Sets cell background color.

        Color should be given as an integer in format 0xAARRGGBB.
        Unsets the background color if None value is given.
        """
        if value is None:
            value = -1
        return self._get_target().setPropertyValue('CellBackColor', value)
    background_color = property(__get_background_color, __set_background_color)

    def __get_border_width(self):
        """
        Gets width of all cell borders (in 1/100 mm).

        Returns 0 if cell borders are different.
        """
        target = self._get_target()
        # Get four borders and test if all of them have same width.
        keys = ('TopBorder', 'RightBorder', 'BottomBorder', 'LeftBorder')
        lines = target.getPropertyValues(keys)
        values = [line.OuterLineWidth for line in lines]
        if any(value != values[0] for value in values):
            return 0
        return values[0]
    def __set_border_width(self, value):
        """
        Sets width of all cell borders (in 1/100 mm).
        """
        target = self._get_target()
        line = uno.createUnoStruct('com.sun.star.table.BorderLine2')
        line.OuterLineWidth = value
        # Set all four borders using one call - this can save even a few seconds
        keys = ('TopBorder', 'RightBorder', 'BottomBorder', 'LeftBorder')
        lines = (line, line, line, line)
        target.setPropertyValues(keys, lines)
    border_width = property(__get_border_width, __set_border_width)

    def __get_one_border_width(self, key):
        """
        Gets width of one border.
        """
        target = self._get_target()
        line = target.getPropertyValue(key)
        return line.OuterLineWidth
    def __set_one_border_width(self, value, key):
        """
        Sets width of one border.
        """
        target = self._get_target()
        line = uno.createUnoStruct('com.sun.star.table.BorderLine2')
        line.OuterLineWidth = value
        target.setPropertyValue(key, line)

    border_left_width = property(functools.partial(__get_one_border_width, key='LeftBorder'),
                                 functools.partial(__set_one_border_width, key='LeftBorder'))
    border_right_width = property(functools.partial(__get_one_border_width, key='RightBorder'),
                                  functools.partial(__set_one_border_width, key='RightBorder'))
    border_top_width = property(functools.partial(__get_one_border_width, key='TopBorder'),
                                functools.partial(__set_one_border_width, key='TopBorder'))
    border_bottom_width = property(functools.partial(__get_one_border_width, key='BottomBorder'),
                                   functools.partial(__set_one_border_width, key='BottomBorder'))

    def __get_inner_border_width(self):
        """
        Gets with of inner border between cells (in 1/100 mm).

        Returns 0 if cell borders are different.
        """
        target = self._get_target()
        tb = target.getPropertyValue('TableBorder')
        horizontal = tb.HorizontalLine.OuterLineWidth
        vertical = tb.VerticalLine.OuterLineWidth
        if horizontal != vertical:
            return 0
        return horizontal
    def __set_inner_border_width(self, value):
        """
        Sets with of inner border between cells (in 1/100 mm).
        """
        target = self._get_target()
        # Inner borders are saved in a TableBorder struct.
        line = uno.createUnoStruct('com.sun.star.table.BorderLine2')
        line.OuterLineWidth = value
        tb = target.getPropertyValue('TableBorder')
        tb.HorizontalLine = tb.VerticalLine = line
        target.setPropertyValue('TableBorder', tb)
    inner_border_width = property(__get_inner_border_width,
                                  __set_inner_border_width)

    # Internal methods:

    def _get_target(self):
        """
        Returns cursor which can be used for most of operations.
        """
        address = self.address
        cursor = self.sheet.cursor
        return cursor.get_target(address.row, address.col,
                                 address.row_count, address.col_count)

    def _convert(self, value):
        if isinstance(value, numbers.Real):
            # OpenOffices raises RuntimeError for integers outside of
            # 32-bit integers
            if -2147483648 <= value <= 2147483647:
                return value
            else:
                return float(value)
        if isinstance(value, string_types):
            return value
        if isinstance(value, datetime.date):
            return self.sheet.document.date_to_number(value)
        if isinstance(value, datetime.time):
            return self.sheet.document.time_to_number(value)
        return text_type(value)

    def _clean_value(self, value):
        """
        Validates and converts value before assigning it to a cell.
        """
        if value is None:
            return value
        return self._convert(value)

    def _clean_formula(self, value):
        """
        Validates and converts formula before assigning it to a cell.
        """
        if value is None:
            return ''
        return self._convert(value)


class Cell(CellRange):
    """
    One cell in a spreadsheet.

    Cells are returned when a sheet (or any other tabular cell range)
    is indexed by two integer numbers.

    """

    __slots__ = ()

    def __get_value(self):
        """
        Gets cell value with as a string or number based on cell type.
        """
        array = self._get_target().getDataArray()
        return array[0][0]
    def __set_value(self, value):
        """
        Sets cell value to a string or number based on the given value.
        """
        array = ((self._clean_value(value),),)
        return self._get_target().setDataArray(array)
    value = property(__get_value, __set_value)

    def __get_formula(self):
        """
        Gets a formula in this cell.

        If this cell contains actual formula then the returned value starts
        with an equal sign but any cell value is returned.
        """
        array = self._get_target().getFormulaArray()
        return array[0][0]
    def __set_formula(self, formula):
        """
        Sets a formula in this cell.

        Any cell value can be set using this method. Actual formulas must
        start with an equal sign.
        """
        array = ((self._clean_formula(formula),),)
        return self._get_target().setFormulaArray(array)
    formula = property(__get_formula, __set_formula)

    @property
    def date(self):
        """
        Returns date value in this cell.

        Converts value from number to datetime.datetime instance.
        """
        return self.sheet.document.date_from_number(self.value)

    @property
    def time(self):
        """
        Returns time value in this cell.

        Converts value from number to datetime.time instance.
        """
        return self.sheet.document.time_from_number(self.value)


class TabularCellRange(CellRange):
    """
    Tabular range of cells.

    Individual cells can be accessed by (row, column) index and
    slice notation can be used for retrieval of sub ranges.

    Instances of this class are returned when a sheet (or any other tabular
    cell range) is sliced in both axes.

    """

    __slots__ = ()

    def __len__(self):
        return self.address.row_count

    def __getitem__(self, key):
        if not isinstance(key, tuple):
            # Expression cells[row] is equal to cells[row, :] and
            # expression cells[start:stop] is equal to cells[start:stop, :].
            key = (key, slice(None))
        elif len(key) != 2:
            raise ValueError('Cell range has two dimensions.')
        address = self.address
        row_val, col_val = key
        if isinstance(row_val, slice):
            start, stop = _clean_slice(row_val, address.row_count)
            row, row_count = address.row + start, stop - start
            single_row = False
        else:
            index = _clean_index(row_val, address.row_count)
            row, row_count = address.row + index, 1
            single_row = True
        if isinstance(col_val, slice):
            start, stop = _clean_slice(col_val, address.col_count)
            col, col_count = address.col + start, stop - start
            single_col = False
        else:
            index = _clean_index(col_val, address.col_count)
            col, col_count = address.col + index, 1
            single_col = True

        address = SheetAddress(row, col, row_count, col_count)
        if single_row and single_col:
            return Cell(self.sheet, address)
        if single_row:
            return HorizontalCellRange(self.sheet, address)
        if single_col:
            return VerticalCellRange(self.sheet, address)
        return TabularCellRange(self.sheet, address)

    def __get_values(self):
        """
        Gets values in this cell range as a tuple of tuples.
        """
        array = self._get_target().getDataArray()
        return array
    def __set_values(self, values):
        """
        Sets values in this cell range from an iterable of iterables.
        """
        # Tuple of tuples is required
        array = tuple(tuple(self._clean_value(col) for col in row) for row in values)
        self._get_target().setDataArray(array)
    values = property(__get_values, __set_values)

    def __get_formulas(self):
        """
        Gets formulas in this cell range as a tuple of tuples.

        If cells contain actual formulas then the returned values start
        with an equal sign  but all values are returned.
        """
        return self._get_target().getFormulaArray()
    def __set_formulas(self, formulas):
        """
        Sets formulas in this cell range from an iterable of iterables.

        Any cell values can be set using this method. Actual formulas must
        start with an equal sign.
        """
        # Tuple of tuples is required
        array = tuple(tuple(self._clean_formula(col) for col in row) for row in formulas)
        self._get_target().setFormulaArray(array)
    formulas = property(__get_formulas, __set_formulas)


class HorizontalCellRange(CellRange):
    """
    Range of cells in one row.

    Individual cells can be accessed by integer index or subranges
    can be retrieved using slice notation.

    Instances of this class are returned if a sheet (or any other tabular
    cell range) is indexed by a row number but columns are sliced.

    """

    __slots__ = ()

    def __len__(self):
        return self.address.col_count

    def __getitem__(self, key):
        if isinstance(key, slice):
            start, stop = _clean_slice(key, len(self))
            address = SheetAddress(self.address.row, self.address.col + start,
                                   self.address.row_count, stop - start)
            return HorizontalCellRange(self.sheet, address)
        else:
            index = _clean_index(key, len(self))
            address = SheetAddress(self.address.row, self.address.col + index)
            return Cell(self.sheet, address)

    def __get_values(self):
        """
        Gets values in this cell range as a tuple.
        """
        array = self._get_target().getDataArray()
        return array[0]
    def __set_values(self, values):
        """
        Sets values in this cell range from an iterable.
        """
        array = (tuple(self._clean_value(v) for v in values),)
        self._get_target().setDataArray(array)
    values = property(__get_values, __set_values)

    def __get_formulas(self):
        """
        Gets formulas in this cell range as a tuple.

        If cells contain actual formulas then the returned values start
        with an equal sign  but all values are returned.
        """
        array = self._get_target().getFormulaArray()
        return array[0]
    def __set_formulas(self, formulas):
        """
        Sets formulas in this cell range from an iterable.

        Any cell values can be set using this method. Actual formulas must
        start with an equal sign.
        """
        array = (tuple(self._clean_formula(v) for v in formulas),)
        return self._get_target().setFormulaArray(array)
    formulas = property(__get_formulas, __set_formulas)


class VerticalCellRange(CellRange):
    """
    Range of cells in one column.

    Individual cells can be accessed by integer index or or subranges
    can be retrieved using slice notation.

    Instances of this class are returned if a sheet (or any other tabular
    cell range) is indexed by a column number but rows are sliced.

    """

    __slots__ = ()

    def __len__(self):
        return self.address.row_count

    def __getitem__(self, key):
        if isinstance(key, slice):
            start, stop = _clean_slice(key, len(self))
            address = SheetAddress(self.address.row  + start, self.address.col,
                                   stop - start, self.address.col_count)
            return HorizontalCellRange(self.sheet, address)
        else:
            index = _clean_index(key, len(self))
            address = SheetAddress(self.address.row  + index, self.address.col)
            return Cell(self.sheet, address)

    def __get_values(self):
        """
        Gets values in this cell range as a tuple.

        This is much more effective than reading cell values one by one.
        """
        array = self._get_target().getDataArray()
        return tuple(itertools.chain.from_iterable(array))
    def __set_values(self, values):
        """
        Sets values in this cell range from an iterable.

        This is much more effective than writing cell values one by one.
        """
        array = tuple((self._clean_value(v),) for v in values)
        self._get_target().setDataArray(array)
    values = property(__get_values, __set_values)

    def __get_formulas(self):
        """
        Gets formulas in this cell range as a tuple.

        If cells contain actual formulas then the returned values start
        with an equal sign  but all values are returned.
        """
        array = self._get_target().getFormulaArray()
        return tuple(itertools.chain.from_iterable(array))
    def __set_formulas(self, formulas):
        """
        Sets formulas in this cell range from an iterable.

        Any cell values can be set using this method. Actual formulas must
        start with an equal sign.
        """
        array = tuple((self._clean_formula(v),) for v in formulas)
        self._get_target().setFormulaArray(array)
    formulas = property(__get_formulas, __set_formulas)


@str_repr
class Sheet(TabularCellRange):
    """
    One sheet in a spreadsheet document.

    This class extends TabularCellRange which means that cells can
    be accessed using index or slice notation.

    Sheet instances can be accessed using sheets property
    of a SpreadsheetDocument class.

    """

    __slots__ = ('document', '_target', 'cursor')

    def __init__(self, document, target):
        self.document = document # Parent SpreadsheetDocument.
        self._target = target # UNO com.sun.star.sheet.XSpreadsheet
        # This cursor will be used for most of the operation in this sheet.
        self.cursor = SheetCursor(target.createCursor())
        # Determine size of this sheet using the created cursor.
        address = SheetAddress(0, 0, self.cursor.row_count, self.cursor.col_count)
        super(Sheet, self).__init__(self, address)

    def __str__(self):
        return text_type(self.name)

    @property
    def index(self):
        """
        Index of this sheet in the document.
        """
        # This should be cached if used more often.
        return self._target.getRangeAddress().Sheet

    def __get_name(self):
        """
        Gets a name of this sheet.
        """
        # This should be cached if used more often.
        return self._target.getName();
    def __set_name(self, value):
        """
        Sets a name of this sheet.
        """
        return self._target.setName(value);
    name = property(__get_name, __set_name)

    @property
    def charts(self):
        target = self._target.getCharts()
        return ChartCollection(self, target)


class SpreadsheetCollection(NamedCollection):
    """
    Collection of spreadsheets in a spreadsheet document.

    Instance of this class is returned via sheets property of
    the SpreadsheetDocument class.

    """

    __slots__ = ('document',)

    def __init__(self, document, target):
        self.document = document # Parent SpreadsheetDocument
        super(SpreadsheetCollection, self).__init__(target)

    def __delitem__(self, key):
        if not isinstance(key, string_types):
            key = self[key].name
        self._delete(key)

    def create(self, name, index=None):
        """
        Creates a new sheet with the given name.

        If an optional index argument is not provided then the created
        sheet is appended at the end. Returns the new sheet.
        """
        if index is None:
            index = len(self)
        self._create(name, index)
        return self[name]

    def copy(self, old_name, new_name, index=None):
        """
        Copies an old sheet with the old_name to a new sheet with new_name.

        If an optional index argument is not provided then the created
        sheet is appended at the end. Returns the new sheet.
        """
        if index is None:
            index = len(self)
        self._copy(old_name, new_name, index)
        return self[new_name]

    # Internal:

    def _factory(self, target):
        return Sheet(self.document, target)

    def _create(self, name, index):
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/sheet/XSpreadsheets.html#insertNewByName
        self._target.insertNewByName(name, index)

    def _copy(self, old_name, new_name, index):
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/sheet/XSpreadsheets.html#copyByName
        self._target.copyByName(old_name, new_name, index)

    def _delete(self, name):
        try:
            self._target.removeByName(name)
        except _NoSuchElementException:
            raise KeyError(name)


class Locale(object):
    """
    Document locale.

    Provides locale number formats. Instances of this class can be
    retrieved from SpreadsheetDocument using get_locale method.

    """

    __slots__ = ('_locale', '_formats')

    def __init__(self, locale, formats):
        self._locale = locale
        self._formats = formats

    def format(self, code):
        """
        Returns one of predefined formats.

        Accepts FORMAT_* constants.
        """
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/util/XNumberFormatTypes.html#getFormatIndex
        return self._formats.getFormatIndex(code, self._locale)


class SpreadsheetDocument(_UnoProxy):
    """
    Spreadsheet document.
    """

    def save(self, path=None, filter_name=None):
        """
        Saves this document to a local file system.

        The optional first argument defaults to the document's path.

        Accept optional second  argument which defines type of
        the saved file. Use one of FILTER_* constants or see list of
        available filters at http://wakka.net/archives/7 or
        http://www.oooforum.org/forum/viewtopic.phtml?t=71294.
        """

        if path is None:
            try:
                self._target.store()
            except _IOException as e:
                raise IOError(e.Message)
            return

        # UNO requires absolute paths
        url = uno.systemPathToFileUrl(os.path.abspath(path))
        if filter_name:
            format_filter = uno.createUnoStruct('com.sun.star.beans.PropertyValue')
            format_filter.Name = 'FilterName'
            format_filter.Value = filter_name
            filters = (format_filter,)
        else:
            filters = ()
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/frame/XStorable.html#storeToURL
        try:
            self._target.storeToURL(url, filters)
        except _IOException as e:
            raise IOError(e.Message)

    def close(self):
        """
        Closes this document.
        """
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/util/XCloseable.html#close
        self._target.close(True)

    def get_locale(self, language=None, country=None, variant=None):
        """
        Returns locale which can be used for access to number formats.
        """
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/lang/Locale.html
        locale = uno.createUnoStruct('com.sun.star.lang.Locale')
        if language:
            locale.Language = language
        if country:
            locale.Country = country
        if variant:
            locale.Variant = variant
        formats = self._target.getNumberFormats()
        return Locale(locale, formats)

    @property
    def sheets(self):
        """
        Collection of sheets in this document.
        """
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/sheet/XSpreadsheetDocument.html#getSheets
        try:
            return self._sheets
        except AttributeError:
            target = self._target.getSheets()
            self._sheets = SpreadsheetCollection(self, target)
        return self._sheets

    def date_from_number(self, value):
        """
        Converts a float value to corresponding datetime instance.
        """
        if not isinstance(value, numbers.Real):
            return None
        delta = datetime.timedelta(days=value)
        return self._null_date + delta

    def date_to_number(self, date):
        """
        Converts a date or datetime instance to a corresponding float value.
        """
        if isinstance(date, datetime.datetime):
            delta = date - self._null_date
        elif isinstance(date, datetime.date):
            delta = date - self._null_date.date()
        else:
            raise TypeError(date)
        return delta.days + delta.seconds / (24.0 * 60 * 60)

    def time_from_number(self, value):
        """
        Converts a float value to corresponding time instance.
        """
        if not isinstance(value, numbers.Real):
            return None
        delta = datetime.timedelta(days=value)
        minutes, second = divmod(delta.seconds, 60)
        hour, minute = divmod(minutes, 60)
        return datetime.time(hour, minute, second)

    def time_to_number(self, time):
        """
        Converts a time instance to a corresponding float value.
        """
        if not isinstance(time, datetime.time):
            raise TypeError(time)
        return ((time.second / 60.0 + time.minute) / 60.0 + time.hour) / 24.0

    # Internal:

    @property
    def _null_date(self):
        """
        Returns date which is represented by a integer 0.
        """
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/util/NumberFormatSettings.html#NullDate
        try:
            return self.__null_date
        except AttributeError:
            number_settings = self._target.getNumberFormatSettings()
            d = number_settings.getPropertyValue('NullDate')
            self.__null_date = datetime.datetime(d.Year, d.Month, d.Day)
        return self.__null_date


def _get_connection_url(hostname, port, pipe=None):
    if pipe:
        conn = 'pipe,name=%s' % pipe
    else:
        conn = 'socket,host=%s,port=%d' % (hostname, port)
    return 'uno:%s;urp;StarOffice.ComponentContext' % conn

def _get_remote_context(resolver, url):
    try:
        return resolver.resolve(url)
    except _NoConnectException:
        raise IOError(resolver, url)

class Desktop(_UnoProxy):
    """
    Access to a running to an OpenOffice.org program.

    Allows to create and open of spreadsheet documents.

    Opens a connection to a running OpenOffice.org program when Desktop
    instance is initialized. If the program OpenOffice.org is restarted
    then the connection is lost all subsequent method calls will fail.

    """

    def __init__(self, hostname='localhost', port=2002, pipe=None):
        url = _get_connection_url(hostname, port, pipe)
        self.local_context = uno.getComponentContext()
        resolver = self.local_context.getServiceManager().createInstanceWithContext('com.sun.star.bridge.UnoUrlResolver', self.local_context)
        self.remote_context = _get_remote_context(resolver, url)
        desktop = self.remote_context.getServiceManager().createInstanceWithContext('com.sun.star.frame.Desktop', self.remote_context)
        super(Desktop, self).__init__(desktop)

    def create_spreadsheet(self):
        """
        Creates a new spreadsheet document.
        """
        url = 'private:factory/scalc'
        document = self._open_url(url)
        return SpreadsheetDocument(document)

    def open_spreadsheet(self, path, as_template=False):
        """
        Opens an exiting spreadsheet document on the local file system.
        """
        extra = ()
        if as_template:
            pv = uno.createUnoStruct('com.sun.star.beans.PropertyValue')
            pv.Name = 'AsTemplate'
            pv.Value = True
            extra += (pv,)
        # UNO requires absolute paths
        url = uno.systemPathToFileUrl(os.path.abspath(path))
        document = self._open_url(url, extra)
        return SpreadsheetDocument(document)

    def _open_url(self, url, extra=()):
        # http://www.openoffice.org/api/docs/common/ref/com/sun/star/frame/XComponentLoader.html#loadComponentFromURL
        try:
            return self._target.loadComponentFromURL(url, '_blank', 0, extra)
        except _IOException as e:
            raise IOError(e.Message)


class LazyDesktop(object):
    """
    Lazy access to a running to Open Office program.

    Provides same interface as a Desktop class but creates connection
    to OpenOffice program when necessary. The advantage of this approach
    is that a LazyDesktop instance can recover from a restart of
    the OpenOffice.org program.

    """

    cls = Desktop

    def __init__(self, hostname='localhost', port=2002, pipe=None):
        self.hostname = hostname
        self.port = port
        self.pipe = pipe

    def create_spreadsheet(self):
        """
        Creates a new spreadsheet document.
        """
        desktop = self.cls(self.hostname, self.port, self.pipe)
        return desktop.create_spreadsheet()

    def open_spreadsheet(self, path, as_template=False):
        """
        Opens an exiting spreadsheet document on the local file system.
        """
        desktop = self.cls(self.hostname, self.port)
        return desktop.open_spreadsheet(path, as_template=as_template)


class NameGenerator(object):
    """
    Generates valid names for Open Office.

    Keeps track of used names and does not return one value twice.

    Names must not contain characters []*?:\/.
    Names (in older versions of OO) must have length of 31 chars maximum.
    Names must be unique (case insensitive).
    """

    max_length = 31

    def __init__(self):
        self._invalid = set([''])

    def __call__(self, name):
        name = text_type(name)
        for char in '[]*?:\/':
            name = name.replace(char, '')
        for i in itertools.count(1):
            if name:
                suffix = ' %d' % i if i > 1 else ''
                candidate = name[:self.max_length - len(suffix)] + suffix
            else:
                candidate = '%d' % i
            if candidate.lower() not in self._invalid:
                break
        self._invalid.add(candidate.lower())
        return candidate
